(function(){
  if(window.__e7)return;window.__e7=1;
  var N=window.Eleven7Native;if(!N)return;
  var blobs={},cu=URL.createObjectURL;
  URL.createObjectURL=function(b){var u=cu.call(URL,b);blobs[u]=b;return u};
  function send(name,blob){
    var f=new FileReader();
    f.onload=function(){N.saveData(name,f.result)};
    f.readAsDataURL(blob);
  }
  function h(a){
    if(!a||!a.hasAttribute||!a.hasAttribute("download"))return false;
    var href=a.href||"";
    var name=a.getAttribute("download")||"download";
    if(href.indexOf("data:")===0){N.saveData(name,href);return true}
    if(href.indexOf("blob:")===0){
      if(blobs[href])send(name,blobs[href]);
      else fetch(href).then(function(r){return r.blob()}).then(function(b){send(name,b)});
      return true;
    }
    return false;
  }
  var P=HTMLAnchorElement.prototype,oc=P.click,od=P.dispatchEvent;
  P.click=function(){if(!h(this))oc.apply(this,arguments)};
  P.dispatchEvent=function(ev){if(ev&&ev.type==="click"&&h(this))return true;return od.apply(this,arguments)};
  document.addEventListener("click",function(e){
    var a=e.target&&e.target.closest?e.target.closest("a[download]"):null;
    if(a&&h(a))e.preventDefault();
  },true);
  window.print=function(){N.print()};
})();
