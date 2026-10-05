const $=s=>document.querySelector(s);
let menu=[],cart={},activeCat="All",staffPin="",lastToken="";

async function api(url,opt={}){const r=await fetch(url,opt);const d=await r.json().catch(()=>({error:"Server error"}));if(!r.ok)throw new Error(d.error||"Request failed");return d}
function money(n){return "₹"+Number(n).toLocaleString("en-IN")}
function toast(s){const t=$("#toast");t.textContent=s;t.style.display="block";clearTimeout(window.tt);window.tt=setTimeout(()=>t.style.display="none",2400)}
function show(id){document.querySelectorAll(".view").forEach(v=>v.classList.remove("active"));$("#"+id).classList.add("active");document.querySelectorAll(".nav").forEach(n=>n.classList.toggle("active",n.dataset.view===id));window.scrollTo({top:0,behavior:"smooth"})}
document.addEventListener("click",e=>{const v=e.target.closest("[data-view]");if(v)show(v.dataset.view)});

async function loadMenu(){
  menu=await api("/api/menu");
  const cats=["All","Meals","Rice","Snacks","Drinks","Desserts"];
  $("#chips").innerHTML=cats.map(c=>`<button class="chip ${c==="All"?"active":""}" data-cat="${c}">${c}</button>`).join("");
  renderMenu();
}
function category(n){
  if(/chai|coffee|soda|lassi|water/i.test(n))return"Drinks";
  if(/samosa|roll|sandwich|chowmein|momos|fries/i.test(n))return"Snacks";
  if(/jamun|rasgulla|ice cream|brownie/i.test(n))return"Desserts";
  if(/rice|biryani|rajma/i.test(n))return"Rice";
  return"Meals";
}
function renderMenu(){
  const q=$("#search").value.toLowerCase();
  const items=menu.filter(x=>(activeCat==="All"||category(x.n)===activeCat)&&x.n.toLowerCase().includes(q));
  $("#menuGrid").innerHTML=items.map(x=>`<article class="food"><img class="food-img" src="/images/${slug(x.n)}.svg" alt="${esc(x.n)}" loading="lazy"><div class="food-top"><div><h3>${esc(x.n)}</h3><p>${category(x.n)} • Freshly prepared</p></div><strong class="price">${money(x.p)}</strong></div><button class="add" onclick="add('${escAttr(x.n)}')">+ Add</button></article>`).join("")||`<div class="empty">No food found.</div>`;
}
function add(n){cart[n]=(cart[n]||0)+1;renderCart();toast("Added to your order")}
function change(n,d){cart[n]=(cart[n]||0)+d;if(cart[n]<=0)delete cart[n];renderCart()}
function renderCart(){
  let total=0,count=0;
  const html=Object.entries(cart).map(([n,q])=>{const p=menu.find(x=>x.n===n).p;total+=p*q;count+=q;return `<div class="cart-line"><div><b>${esc(n)}</b><br><small>${money(p)} each</small></div><div><b>${money(p*q)}</b><div class="qty"><button onclick="change('${escAttr(n)}',-1)">−</button>${q}<button onclick="change('${escAttr(n)}',1)">+</button></div></div></div>`}).join("");
  $("#cartItems").innerHTML=html||`<div class="empty">Your cart is empty.<br>Add something delicious.</div>`;
  $("#cartTotal").textContent=money(total);$("#cartCount").textContent=count;$("#checkoutBtn").disabled=!count;
  $("#checkoutTotal").textContent=money(total);return total;
}
$("#search").addEventListener("input",renderMenu);
$("#chips").addEventListener("click",e=>{if(!e.target.matches(".chip"))return;activeCat=e.target.dataset.cat;document.querySelectorAll(".chip").forEach(x=>x.classList.remove("active"));e.target.classList.add("active");renderMenu()});
$("#checkoutBtn").onclick=()=>show("checkout");

$("#checkoutForm").addEventListener("submit",async e=>{
 e.preventDefault();const fd=new FormData(e.target);const items=Object.entries(cart).map(([n,q])=>`${n}~${q}`).join("|");
 fd.set("items",items);
 try{const d=await api("/api/orders",{method:"POST",body:new URLSearchParams(fd)});lastToken=d.tok;$("#token").textContent=d.tok;$("#successTotal").textContent=money(d.total);cart={};renderCart();e.target.reset();show("success");}
 catch(err){toast(err.message)}
});
$("#trackNow").onclick=()=>{$("#trackToken").value=lastToken;show("track");track()};
$("#trackBtn").onclick=track;
$("#trackToken").addEventListener("keydown",e=>{if(e.key==="Enter")track()});
async function track(){
 const token=$("#trackToken").value.trim().toUpperCase();if(!token)return toast("Enter your token");
 try{const d=await api("/api/order?token="+encodeURIComponent(token));const statuses=["New","Preparing","Ready","Collected"];const idx=statuses.indexOf(d.status);$("#trackResult").innerHTML=`<div class="result"><div class="order-head"><h3>${esc(d.tok)}</h3><span class="status">${esc(d.status)}</span></div><p>Customer: ${esc(d.name)}</p><p>Items: ${esc(d.items.replaceAll("|",", "))}</p><p>Total: <b>${money(d.total)}</b></p><div class="steps">${statuses.map((s,i)=>`<div class="step ${i<=idx?"done":""}" title="${s}"></div>`).join("")}</div><small class="muted">New → Preparing → Ready → Collected</small></div>`}
 catch(err){$("#trackResult").innerHTML=`<div class="result">${esc(err.message)}</div>`}
}

$("#staffForm").addEventListener("submit",async e=>{e.preventDefault();staffPin=$("#pin").value;try{await loadOrders();$("#staffLogin").classList.add("hidden");$("#dashboard").classList.remove("hidden")}catch(err){toast(err.message)}});
$("#refresh").onclick=loadOrders;
async function loadOrders(){
 const data=await api("/api/orders",{headers:{"X-Staff-Pin":staffPin}});
 $("#orders").innerHTML=data.length?data.map(o=>`<div class="order"><div class="order-head"><div><h3>${esc(o.tok)} · ${esc(o.nm)}</h3><p>Roll: ${esc(o.roll)} · Phone: ${esc(o.ph)}<br>Pickup: ${esc(o.time)}</p></div><span class="status">${esc(o.status)}</span></div><p><b>Items:</b> ${esc(o.items.join(", "))}<br><b>Total:</b> ${money(o.total)}${o.note?`<br><b>Note:</b> ${esc(o.note)}`:""}</p><div class="order-actions">${["New","Preparing","Ready","Collected"].map(s=>`<button onclick="setStatus(${o.id},'${s}')">${s}</button>`).join("")}</div></div>`).join(""):`<div class="empty">No orders yet.</div>`;
}
async function setStatus(id,status){try{await api("/api/status",{method:"POST",headers:{"X-Staff-Pin":staffPin,"Content-Type":"application/x-www-form-urlencoded"},body:new URLSearchParams({id,status})});await loadOrders();toast("Order updated")}catch(err){toast(err.message)}}
function slug(s){return String(s).toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"")}
function esc(s){return String(s??"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[m]))}
function escAttr(s){return String(s).replace(/['\\]/g,"\\$&")}
loadMenu().catch(e=>toast(e.message));
setInterval(()=>{ if(document.querySelector("#dashboard")?.classList.contains("hidden")===false) loadOrders().catch(()=>{}); },10000);
const dt=document.querySelector('input[name="time"]');
if(dt){ const d=new Date(Date.now()+10*60*1000); d.setMinutes(d.getMinutes()-d.getTimezoneOffset()); dt.min=d.toISOString().slice(0,16); }

