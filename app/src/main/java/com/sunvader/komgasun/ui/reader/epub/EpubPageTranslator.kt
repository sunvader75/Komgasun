package com.sunvader.komgasun.ui.reader.epub

import com.sunvader.komgasun.ui.reader.MlKitTextTranslator
import com.sunvader.komgasun.ui.reader.TextBlockTranslator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bloque de texto visible en la página actual del WebView EPUB.
 */
@Serializable
data class EpubVisibleBlock(
    val tag: String,
    val text: String,
    val noIndent: Boolean = false
)

/**
 * Estado de la traducción bajo demanda de la página actual del lector EPUB.
 */
sealed interface EpubTranslationState {
    data object Idle : EpubTranslationState
    data object Loading : EpubTranslationState
    data class Success(
        val chapterIndex: Int,
        val page: Int,
        val blocks: List<EpubVisibleBlock>
    ) : EpubTranslationState
    data object Empty : EpubTranslationState
    data class Error(val message: String) : EpubTranslationState
}

/**
 * Traduce los bloques visibles de una página EPUB manteniendo su estructura semántica (`P`, `H1`..`H6`, etc.).
 */
class EpubPageTranslator(
    private val textTranslator: TextBlockTranslator = MlKitTextTranslator()
) {
    suspend fun translateVisibleBlocks(blocks: List<EpubVisibleBlock>): Result<List<EpubVisibleBlock>> {
        val nonBlank = blocks.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return Result.success(emptyList())

        return textTranslator.translateBlocks(nonBlank.map { normalizeEpubBlockText(it.text) })
            .map { translatedList ->
                nonBlank.mapIndexedNotNull { idx, block ->
                    val translated = translatedList.getOrNull(idx)?.trim().orEmpty()
                    if (translated.isEmpty()) null else block.copy(text = translated)
                }
            }
    }

    fun close() {
        textTranslator.close()
    }
}

internal fun normalizeEpubBlockText(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim()

internal object EpubPageTranslationScripts {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseExtractedBlocksJson(rawEvaluateResult: String?): List<EpubVisibleBlock> {
        if (rawEvaluateResult.isNullOrBlank() || rawEvaluateResult == "null") return emptyList()
        return try {
            // evaluateJavascript devuelve un string JSON (o un array serializado como string JSON).
            val unwrapped = if (rawEvaluateResult.startsWith("\"") && rawEvaluateResult.endsWith("\"")) {
                json.decodeFromString<String>(rawEvaluateResult)
            } else {
                rawEvaluateResult
            }
            json.decodeFromString<List<EpubVisibleBlock>>(unwrapped)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeBlocksJson(blocks: List<EpubVisibleBlock>): String =
        json.encodeToString(blocks)

    /**
     * Extrae únicamente el texto visible en el viewport actual `[0, window.innerWidth)` del WebView EPUB,
     * recortando por palabras los párrafos que cruzan de una columna a otra.
     */
    fun extractVisibleBlocksScript(): String = """
        (function(){
          const viewport=document.body && document.body.firstElementChild;
          const pages=viewport && viewport.firstElementChild;
          const w=window.innerWidth, h=window.innerHeight;
          if(!pages || w<1 || h<1) return JSON.stringify([]);
          const blockTags=new Set(['P','H1','H2','H3','H4','H5','H6','LI','BLOCKQUOTE','PRE']);
          function blockAncestor(node){
            let cur=node.parentElement;
            while(cur && cur!==pages){
              if(blockTags.has(cur.tagName)) return cur;
              cur=cur.parentElement;
            }
            return node.parentElement || pages;
          }
          const walker=document.createTreeWalker(pages, NodeFilter.SHOW_TEXT, null);
          const range=document.createRange();
          const groups=[];
          let lastBlock=null;
          let node;
          while((node=walker.nextNode())){
            const text=node.nodeValue || '';
            if(!text.trim()) continue;
            range.selectNodeContents(node);
            const rect=range.getBoundingClientRect();
            if(rect.width<=0 || rect.height<=0 || rect.right<=4 || rect.left>=w-4) continue;
            let visibleSlice='';
            let startedMidNode=false;
            if(rect.left>=-4 && rect.right<=w+4 && rect.bottom>0 && rect.top<h){
              visibleSlice=text;
            } else {
              const tokenRe=/\S+|\s+/g;
              let match, firstStart=-1, lastEnd=-1;
              while((match=tokenRe.exec(text))!==null){
                const tok=match[0];
                if(!tok.trim()) continue;
                range.setStart(node, match.index);
                range.setEnd(node, match.index+tok.length);
                const r=range.getBoundingClientRect();
                const cx=(r.left+r.right)*0.5;
                if(r.width>0 && r.height>0 && cx>=0 && cx<=w && r.bottom>0 && r.top<h){
                  if(firstStart===-1) firstStart=match.index;
                  lastEnd=match.index+tok.length;
                }
              }
              if(firstStart!==-1 && lastEnd>firstStart){
                visibleSlice=text.slice(firstStart, lastEnd);
                startedMidNode=firstStart>0;
              }
            }
            const cleaned=visibleSlice.replace(/\s+/g,' ').trim();
            if(!cleaned) continue;
            const blk=blockAncestor(node);
            const tag=blockTags.has(blk.tagName)?blk.tagName:'P';
            if(lastBlock && lastBlock.el===blk){
              lastBlock.text += ' ' + cleaned;
            } else {
              const blkRect=blk.getBoundingClientRect();
              const continuesFromPrev=startedMidNode || (blkRect.left < -8);
              lastBlock={el:blk, tag:tag, text:cleaned, noIndent:continuesFromPrev};
              groups.push(lastBlock);
            }
          }
          return JSON.stringify(groups.map(g=>({tag:g.tag, text:g.text, noIndent:g.noIndent})));
        })()
    """.trimIndent()

    /**
     * Renderiza la capa de traducción sobre el viewport actual heredando la tipografía y colores activos,
     * y ajusta el tamaño de letra si la traducción en español supera la altura de la pantalla.
     */
    fun applyTranslatedBlocksScript(blocks: List<EpubVisibleBlock>, fontSizeSp: Int, isDarkTheme: Boolean, useOriginalFonts: Boolean): String {
        val serializedBlocks = encodeBlocksJson(blocks)
        val bg = if (isDarkTheme) "#121212" else "white"
        val fg = if (isDarkTheme) "#e0e0e0" else "#212121"
        return """
            (function(){
              const viewport=document.body && document.body.firstElementChild;
              const pages=viewport && viewport.firstElementChild;
              if(!viewport || !pages) return false;
              let layer=viewport.querySelector('[data-epub-translation="layer"]');
              if(!layer){
                layer=document.createElement('div');
                layer.setAttribute('data-epub-translation','layer');
                viewport.appendChild(layer);
              }
              const w=window.innerWidth, h=window.innerHeight;
              const baseFont=${fontSizeSp.coerceIn(12, 32)};
              const fontFamily=${if (useOriginalFonts) "window.getComputedStyle(pages).fontFamily || 'serif'" else "'\"Literata\",\"Merriweather\",\"Georgia\",\"Droid Serif\",serif'"};
              layer.style.cssText='position:fixed!important;inset:0!important;width:'+w+'px!important;height:'+h+'px!important;z-index:9999!important;overflow:hidden!important;margin:0!important;padding:0!important;background:$bg!important;color:$fg!important;font-family:'+fontFamily+'!important;font-size:'+baseFont+'px!important;line-height:1.62!important;';
              layer.replaceChildren();
              const items=$serializedBlocks;
              for(let i=0;i<items.length;i++){
                const item=items[i];
                const tag=['P','H1','H2','H3','H4','H5','H6','LI','BLOCKQUOTE','PRE'].includes(item.tag)?item.tag:'P';
                const el=document.createElement(tag.toLowerCase());
                el.textContent=item.text;
                if(tag==='P' && item.noIndent){
                  el.style.setProperty('text-indent','0','important');
                }
                layer.appendChild(el);
              }
              let scale=1.0;
              let lineHeight=1.62;
              while(layer.scrollHeight > h && scale > 0.70){
                scale -= 0.03;
                lineHeight = Math.max(1.28, lineHeight - 0.04);
                layer.style.setProperty('font-size', (baseFont * scale).toFixed(2) + 'px', 'important');
                layer.style.setProperty('line-height', lineHeight.toFixed(2), 'important');
              }
              return true;
            })()
        """.trimIndent()
    }

    fun clearTranslatedBlocksScript(): String = """
        (function(){
          const layer=document.querySelector('[data-epub-translation="layer"]');
          if(layer) layer.remove();
          return true;
        })()
    """.trimIndent()
}
