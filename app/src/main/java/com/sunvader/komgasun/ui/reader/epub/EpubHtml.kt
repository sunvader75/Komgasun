package com.sunvader.komgasun.ui.reader.epub

/** El marcado de la publicación nunca se interpola en el documento de arranque de confianza. */
object EpubHtml {
    const val ORIGIN = "https://epub.invalid"
    val shell = """<!doctype html><html data-epub-shell="app"><head><meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'none'; style-src 'unsafe-inline' https://epub.invalid; img-src https://epub.invalid blob:; font-src https://epub.invalid; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        </head><body></body></html>""".trimIndent()

    /**
     * Genera el script de instalación; los argumentos quoted deben ser expresiones JSON ya escapadas,
     * no HTML ni URL sin citar. DOMParser crea un árbol inerte y se eliminan elementos y atributos
     * activos antes de adoptarlo; la CSP añade una segunda barrera, también para las URL de CSS.
     * Solo transforma SVG pasivo de una imagen ráster permitida; nunca adopta nodos SVG.
     * Si se indica documento esperado, comprueba ubicación, base y marca de la shell en la misma
     * evaluación. La API instalada es inmutable; una segunda instalación en ese documento es inocua.
     * Devuelve código JavaScript, no lo ejecuta ni garantiza que las imágenes puedan decodificarse.
     */
    fun install(quotedHtml: String, quotedRasterUrls: String = "[]", quotedChapterBase: String = "null", quotedDocumentUrl: String = "null"): String = """
        (function(){
          const expectedDocument=$quotedDocumentUrl;
          if(expectedDocument && (location.href!==expectedDocument || location.origin!=='https://epub.invalid' || document.baseURI!==expectedDocument || document.documentElement.getAttribute('data-epub-shell')!=='app'))return false;
          // Only this app creates the immutable API. Same-document completion is a no-op;
          // chapter changes always navigate to a new shell, never delete/replace this property.
          if(Object.prototype.hasOwnProperty.call(window,'__epub'))return !!expectedDocument;
          const parsed=new DOMParser().parseFromString($quotedHtml,'text/html');
          // Only a passive SVG containing one raster image is translated; never adopt SVG nodes.
          const allowedRasterUrls=new Set($quotedRasterUrls);
          const trustedBase=$quotedChapterBase || document.baseURI;
          parsed.querySelectorAll('svg').forEach(svg=>{
            const passive=(node,allowed)=>Array.from(node.attributes).every(a=>allowed.includes(a.name));
            if(svg.namespaceURI!=='http://www.w3.org/2000/svg' ||
              !passive(svg,['xmlns','xmlns:xlink','version','width','height','viewBox','preserveAspectRatio']) ||
              svg.children.length!==1 || Array.from(svg.childNodes).some(n=>n.nodeType===3 && n.textContent.trim()))return;
            const image=svg.children[0];
            if(image.localName!=='image' || image.namespaceURI!==svg.namespaceURI || image.childNodes.length!==0 ||
              !passive(image,['width','height','href','xlink:href']))return;
            const refs=['href','xlink:href'].filter(a=>image.hasAttribute(a));
            if(refs.length<1)return;
            const ref=image.getAttribute(refs[0]);
            // Reject schemes, absolute paths, query/fragment and encoded traversal before URL normalization.
            if(!ref || ref!==ref.trim() || /[:\\?#%]/.test(ref) || ref.startsWith('/') || ref.split('/').some(p=>!p))return;
            let url;try{url=new URL(ref,trustedBase);}catch(e){return;}
            if(url.protocol!=='https:' || url.origin!=='https://epub.invalid' || url.search || url.hash || !allowedRasterUrls.has(url.href))return;
            const img=document.createElement('img');img.setAttribute('src',url.href);
            if(image.getAttribute('width'))img.setAttribute('width',image.getAttribute('width'));
            if(image.getAttribute('height'))img.setAttribute('height',image.getAttribute('height'));
            svg.replaceWith(img);
          });
          parsed.querySelectorAll('script,base,meta,iframe,frame,frameset,object,embed,applet,form,input,button,textarea,select,svg,math,audio,video,source').forEach(e=>e.remove());
          parsed.querySelectorAll('*').forEach(e=>Array.from(e.attributes).forEach(a=>{
            if(a.name.toLowerCase().startsWith('on') || ['srcdoc','action','formaction','target','download','autofocus','loading','id','name'].includes(a.name.toLowerCase())) e.removeAttribute(a.name);
          }));
          parsed.querySelectorAll('link').forEach(e=>{if(e.rel!=='stylesheet' || !e.getAttribute('href')?.trim())e.remove();else e.removeAttribute('disabled');});
          // Publication data/external image attributes never reach the live document.
          const sourceKinds=new WeakMap();
          parsed.querySelectorAll('img').forEach(e=>sourceKinds.set(e,!e.hasAttribute('src')?'MISSING_ATTR':!e.getAttribute('src')?'EMPTY_ATTR':'ATTR'));
          parsed.querySelectorAll('*').forEach(e=>{
            e.removeAttribute('srcset');e.removeAttribute('background');e.removeAttribute('poster');
            if(e.hasAttribute('src')) {
              let url;try{if(e.getAttribute('src'))url=new URL(e.getAttribute('src'),trustedBase);}catch(_){}
              if(e.tagName!=='IMG' || !url || !allowedRasterUrls.has(url.href))e.removeAttribute('src');
              else e.setAttribute('src',url.href);
            }
          });
          let pendingStyles=0;
          parsed.querySelectorAll('link').forEach(e=>{
            pendingStyles++;
            const done=()=>{pendingStyles--;e.removeEventListener('load',done);e.removeEventListener('error',done);};
            e.addEventListener('load',done);e.addEventListener('error',done);
          });
          // Keep publisher body font selectors and inline family without adopting layout styles.
          document.documentElement.style.fontFamily='serif';
          document.body.className=parsed.body.className;
          document.body.style.fontFamily=parsed.body.style.fontFamily;
          const viewport=document.createElement('div'), pages=document.createElement('div');
          document.body.replaceChildren(viewport); viewport.append(pages);
          Array.from(parsed.head.children).forEach(e=>{if(['STYLE','LINK'].includes(e.tagName))pages.append(e);});
          Array.from(parsed.body.childNodes).forEach(e=>pages.append(e));
          const css=document.createElement('style'); css.textContent=`
            html,body {width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;}
            * {box-sizing:border-box;max-width:100%!important;position:static!important;float:none!important;transform:none!important;}
            p,span,div,h1,h2,h3,h4,h5,h6 {overflow-wrap:anywhere;}
            p {margin-top:0;margin-bottom:0.35em;text-indent:1.5em;text-align:justify;text-justify:inter-word;hyphens:auto;-webkit-hyphens:auto;}
            h1,h2,h3,h4,h5,h6 {text-indent:0;text-align:center;margin-top:1.2em;margin-bottom:0.6em;break-after:avoid;}
            pre,table {text-indent:0;max-width:100%!important;}
            img {display:block!important;margin:0 auto!important;width:auto!important;height:auto!important;max-width:100%!important;max-height:100%!important;object-fit:contain!important;break-inside:avoid;}
            table {table-layout:fixed;width:100%;} pre {white-space:pre-wrap!important;break-inside:auto!important;}
          `; document.head.append(css);
          const readerTypography=document.createElement('style');document.head.append(readerTypography);
          let typographyMode=null;
          let fraction=0, font=18, dark=true;
          const images=Array.from(pages.querySelectorAll('img'));
          const validatedSources=images.map(e=>e.getAttribute('src') || '');
          const started=performance.now(), retried=new Set();
          const api={settled:function(){
            return pendingStyles===0 && images.every(e=>e.complete && e.naturalWidth>0) && (!document.fonts || document.fonts.status==='loaded');
          },images:function(){return images.slice(0,256).map((e,i)=>{
            const r=e.getBoundingClientRect();return [i,validatedSources[i],e.complete,e.naturalWidth,e.naturalHeight,Math.round(r.width),Math.round(r.height),performance.now()-started,sourceKinds.get(e)||'MISSING_ATTR',e.currentSrc?'CURRENT_SRC':'ATTR',allowedRasterUrls.size,$quotedChapterBase?'APP_CHAPTER':'DOCUMENT_FALLBACK'];
          });},recover:function(i,source){
            if(!Number.isInteger(i)||i<0||i>=images.length||retried.has(i)||!/^data:image\/(png|jpeg|jpg|gif|webp);base64,[A-Za-z0-9+/=]+$/.test(source))return false;

            retried.add(i);
            const parts=source.split(','),raw=atob(parts[1]),bytes=new Uint8Array(raw.length);
            for(let n=0;n<raw.length;n++)bytes[n]=raw.charCodeAt(n);
            const url=URL.createObjectURL(new Blob([bytes],{type:parts[0].slice(5,-7)}));
            const release=()=>{URL.revokeObjectURL(url);images[i].removeEventListener('load',release);images[i].removeEventListener('error',release);};
            images[i].addEventListener('load',release);images[i].addEventListener('error',release);
            images[i].src=url;return true;
          },apply:function(f,size,theme,originalFonts=false){
            fraction=Math.max(0,Math.min(1,f));font=size;dark=theme;
            if(typographyMode!==originalFonts){
              typographyMode=originalFonts;
              readerTypography.textContent=originalFonts?'':'* {font-size:inherit!important;line-height:inherit!important;} h1,h2,h3,h4,h5,h6 {line-height:1.3!important;}';
            }
            const w=window.innerWidth,h=window.innerHeight;
            if(w<1||h<1)return [0,0];
            viewport.style.cssText='position:fixed!important;inset:0!important;overflow:hidden!important;';
            pages.style.cssText='position:relative!important;margin:0!important;padding:0!important;width:'+w+'px!important;height:'+h+'px!important;column-width:'+w+'px!important;column-gap:0!important;column-fill:auto!important;overflow:visible!important;transform:none!important;line-height:1.65!important;font-size:'+font+'px!important;color:'+(dark?'#e0e0e0':'#212121')+'!important;background:'+(dark?'#121212':'white')+'!important;';
            if(!originalFonts)pages.style.setProperty('font-family','"Literata","Merriweather","Georgia","Droid Serif",serif','important');
            document.body.style.background=dark?'#121212':'white';
            const n=Math.max(1,Math.ceil(pages.scrollWidth/w));
            // Do not commit an initial/end locator against an image/font/CSS placeholder layout.
            if(pendingStyles>0 || (performance.now()-started<1500 && images.some(e=>!e.complete || e.naturalWidth===0)) || (document.fonts && document.fonts.status==='loading'))return [0,0];
            const p=Math.round(fraction*(n-1));
            pages.style.setProperty('transform','translateX('+(-p*w)+'px)','important');
            return [p,n];
          }};
          Object.defineProperty(window,'__epub',{value:api,writable:false,configurable:false});
          return true;
        })()
    """.trimIndent()
}
