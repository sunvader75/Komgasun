package com.sunvader.komgasun.ui.reader.epub

/** Publication markup is never interpolated into this trusted bootstrap document. */
object EpubHtml {
    const val ORIGIN = "https://epub.invalid"
    val shell = """<!doctype html><html><head><meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'none'; style-src 'unsafe-inline' https://epub.invalid; img-src https://epub.invalid; font-src https://epub.invalid; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'">
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        </head><body></body></html>""".trimIndent()

    // DOMParser produces an inert tree. Active elements and attributes are removed before adoption.
    // CSP is a second boundary, including for publication CSS URLs; no inline script is trusted.
    fun install(quotedHtml: String): String = """
        (function(){
          const parsed=new DOMParser().parseFromString($quotedHtml,'text/html');
          parsed.querySelectorAll('script,base,meta,iframe,frame,frameset,object,embed,applet,form,input,button,textarea,select,svg,math,audio,video,source').forEach(e=>e.remove());
          parsed.querySelectorAll('*').forEach(e=>Array.from(e.attributes).forEach(a=>{
            if(a.name.toLowerCase().startsWith('on') || ['srcdoc','action','formaction','target','download','autofocus','loading','id','name'].includes(a.name.toLowerCase())) e.removeAttribute(a.name);
          }));
          parsed.querySelectorAll('link').forEach(e=>{if(e.rel!=='stylesheet' || !e.getAttribute('href')?.trim())e.remove();else e.removeAttribute('disabled');});
          let pendingStyles=0;
          parsed.querySelectorAll('link').forEach(e=>{
            pendingStyles++;
            const done=()=>{pendingStyles--;e.removeEventListener('load',done);e.removeEventListener('error',done);};
            e.addEventListener('load',done);e.addEventListener('error',done);
          });
          const viewport=document.createElement('div'), pages=document.createElement('div');
          document.body.replaceChildren(viewport); viewport.append(pages);
          Array.from(parsed.head.children).forEach(e=>{if(['STYLE','LINK'].includes(e.tagName))pages.append(e);});
          Array.from(parsed.body.childNodes).forEach(e=>pages.append(e));
          const css=document.createElement('style'); css.textContent=`
            html,body {width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;}
            * {box-sizing:border-box;max-width:100%!important;overflow-wrap:anywhere;position:static!important;float:none!important;transform:none!important;font-size:inherit!important;line-height:inherit!important;}
            img,table,pre {max-width:100%!important;}
            img {object-fit:contain;height:auto;max-height:80vh!important;break-inside:avoid;}
            table {table-layout:fixed;width:100%;} pre {white-space:pre-wrap!important;break-inside:auto!important;}
          `; document.head.append(css);
          let fraction=0, font=18, dark=true;
          const api={apply:function(f,size,theme){
            fraction=Math.max(0,Math.min(1,f));font=size;dark=theme;
            const w=window.innerWidth,h=window.innerHeight;
            if(w<1||h<1)return [0,0];
            viewport.style.cssText='position:fixed!important;inset:0!important;overflow:hidden!important;';
            pages.style.cssText='position:relative!important;margin:0!important;padding:0!important;width:'+w+'px!important;height:'+h+'px!important;column-width:'+w+'px!important;column-gap:0!important;column-fill:auto!important;overflow:visible!important;transform:none!important;line-height:1.6!important;font-size:'+font+'px!important;color:'+(dark?'#e0e0e0':'#212121')+'!important;background:'+(dark?'#121212':'white')+'!important;';
            document.body.style.background=dark?'#121212':'white';
            const n=Math.max(1,Math.ceil(pages.scrollWidth/w));
            // Do not commit an initial/end locator against an image/font/CSS placeholder layout.
            if(pendingStyles>0 || Array.from(pages.querySelectorAll('img')).some(e=>!e.complete) || (document.fonts && document.fonts.status==='loading'))return [0,0];
            const p=Math.round(fraction*(n-1));
            pages.style.setProperty('transform','translateX('+(-p*w)+'px)','important');
            return [p,n];
          }};
          Object.defineProperty(window,'__epub',{value:api,writable:false,configurable:false});
          return true;
        })()
    """.trimIndent()
}
