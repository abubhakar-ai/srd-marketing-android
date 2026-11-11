(function(){
if(window.__srdPoly)return;window.__srdPoly=1;
var N=window.SRDNative;if(!N)return;
try{var AL='microphone *; camera *; autoplay *';var fixF=function(f){try{var a=f.getAttribute('allow')||'';if(a.indexOf('microphone')<0)f.setAttribute('allow',(a?a+'; ':'')+AL)}catch(e){}};document.querySelectorAll('iframe').forEach(fixF);new MutationObserver(function(ms){ms.forEach(function(m){m.addedNodes.forEach(function(n){if(n.tagName==='IFRAME')fixF(n);else if(n.querySelectorAll)n.querySelectorAll('iframe').forEach(fixF)})})}).observe(document,{childList:true,subtree:true})}catch(e){}
try{var SS='srd_session',seen=false;if(N.getSession&&!localStorage.getItem(SS)){var t0=N.getSession();if(t0)localStorage.setItem(SS,t0)}setInterval(function(){try{var t=localStorage.getItem(SS);if(t){seen=true;if(N.saveSession)N.saveSession(t)}else if(seen){seen=false;if(N.saveSession)N.saveSession('')}}catch(e){}},4000)}catch(e){}
function read(){try{return JSON.parse(N.getLoc())}catch(e){return {ok:false,reason:'ERR'}}}
function pos(r){return {coords:{latitude:r.lat,longitude:r.lng,accuracy:r.acc,altitude:null,altitudeAccuracy:null,heading:null,speed:null},timestamp:r.t}}
function err(code,msg){return {code:code,message:msg,PERMISSION_DENIED:1,POSITION_UNAVAILABLE:2,TIMEOUT:3}}
function get(ok,bad,opt){
var to=Math.min((opt&&opt.timeout)||15000,20000),t0=Date.now(),asked=false;
(function tick(){
var r=read();
if(r.ok){ok(pos(r));return}
if(!asked){asked=true;try{N.ensure()}catch(e){}}
if(Date.now()-t0>=to){if(bad)bad(err(r.reason==='NO_PERMISSION'?1:(r.reason==='GPS_OFF'?2:3),r.reason||'timeout'));return}
setTimeout(tick,500)})()}
var W={},wid=0;
var g={
getCurrentPosition:get,
watchPosition:function(ok,bad,opt){var id=++wid,last=0;W[id]=setInterval(function(){var r=read();if(r.ok&&r.t!==last){last=r.t;ok(pos(r))}},2000);get(ok,bad,opt);return id},
clearWatch:function(id){clearInterval(W[id]);delete W[id]}};
try{Object.defineProperty(navigator,'geolocation',{value:g,configurable:true})}catch(e){}
function isDl(a){return a&&a.tagName==='A'&&a.download&&/^(blob:|data:)/.test(a.href)}
function save(a){
fetch(a.href).then(function(r){return r.blob()}).then(function(b){
var fr=new FileReader();
fr.onload=function(){N.saveFile(String(fr.result).split(',')[1]||'',a.download,b.type||'application/octet-stream')};
fr.readAsDataURL(b)})}
try{
var oc=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){if(isDl(this)){save(this);return}return oc.apply(this,arguments)};
var od=EventTarget.prototype.dispatchEvent;
EventTarget.prototype.dispatchEvent=function(e){if(e&&e.type==='click'&&isDl(this)){save(this);return true}return od.apply(this,arguments)};
document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a');if(isDl(a)){e.preventDefault();save(a)}},true)
}catch(e){}
try{if(window.self===window.top){var BT='created by a Google Apps Script user';var srdBannerKill=function(){try{var root=document.body||document.documentElement;if(!root)return;var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);var n,hit=null;while((n=w.nextNode())){if(n.nodeValue&&n.nodeValue.indexOf(BT)>-1){hit=n.parentElement;break}}if(!hit)return;var e=hit;while(e.parentElement&&e.parentElement!==document.body&&e.parentElement!==document.documentElement&&e.parentElement.textContent.length<260&&!e.parentElement.querySelector('iframe'))e=e.parentElement;e.style.setProperty('display','none','important');var f=document.getElementById('sandboxFrame')||document.querySelector('iframe');if(f){f.style.setProperty('top','0','important');f.style.setProperty('margin-top','0','important');void 0}}catch(x){}};setInterval(srdBannerKill,700);srdBannerKill()}}catch(e){}
})();
