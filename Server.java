import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Server {
  static final Map<String,Integer> MENU = new LinkedHashMap<>();
  static {
    MENU.put("Poha",25); MENU.put("Idli Sambar (3 pcs)",35); MENU.put("Aloo Paratha",40);
    MENU.put("Bread Omelette",35); MENU.put("Veg Thali",70); MENU.put("Egg Curry Rice",60);
    MENU.put("Fish Curry Rice",90); MENU.put("Chicken Biryani",110); MENU.put("Veg Biryani",80);
    MENU.put("Rajma Chawal",65); MENU.put("Paneer Butter Masala + Roti",95);
    MENU.put("Veg Fried Rice",65); MENU.put("Samosa (2 pcs)",20); MENU.put("Veg Roll",35);
    MENU.put("Egg Roll",40); MENU.put("Vegetable Sandwich",30); MENU.put("Chowmein",50);
    MENU.put("Veg Momos (6 pcs)",45); MENU.put("French Fries",40); MENU.put("Masala Chai",10);
    MENU.put("Coffee",15); MENU.put("Cold Coffee",40); MENU.put("Lemon Soda",25);
    MENU.put("Mango Lassi",35); MENU.put("Packaged Water",20); MENU.put("Gulab Jamun (2 pcs)",30);
    MENU.put("Rasgulla (2 pcs)",30); MENU.put("Ice Cream Cup",25); MENU.put("Brownie",45);
  }

  static final String PIN = System.getenv().getOrDefault("STAFF_PIN", "");
  static final Path DATA = Paths.get(System.getenv().getOrDefault("DATA_DIR", "."), "orders.tsv").toAbsolutePath().normalize();
  static final Path PUBLIC = Paths.get("public").toAbsolutePath().normalize();
  static final List<String[]> orders = new ArrayList<>();
  static final Set<String> STATUS = Set.of("New","Preparing","Ready","Collected");

  public static void main(String[] args) throws Exception {
    load();
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
    HttpServer s = HttpServer.create(new InetSocketAddress(port),0);
    s.createContext("/api/menu",Server::menu);
    s.createContext("/api/orders",Server::ordersApi);
    s.createContext("/api/order",Server::trackOrder);
    s.createContext("/api/status",Server::status);
    s.createContext("/health",Server::health);
    s.createContext("/",Server::files);
    s.start();
    System.out.println("QuickBite listening on port "+port);
  }

  static void health(HttpExchange x) throws IOException {
    if (!x.getRequestMethod().equals("GET")) { send(x,405,"application/json","{\"error\":\"Method not allowed\"}"); return; }
    send(x,200,"application/json","{\"ok\":true,\"service\":\"quickbite\"}");
  }

  static void menu(HttpExchange x) throws IOException {
    if (cors(x)) return;
    if (!x.getRequestMethod().equals("GET")) { send(x,405,"application/json","{\"error\":\"Method not allowed\"}"); return; }
    StringBuilder b=new StringBuilder("[");
    for (var e:MENU.entrySet()) b.append(b.length()>1?",":"")
      .append("{\"n\":\"").append(j(e.getKey())).append("\",\"p\":").append(e.getValue()).append("}");
    send(x,200,"application/json",b.append("]").toString());
  }

  static void ordersApi(HttpExchange x) throws IOException {
    if (cors(x)) return;
    if (!x.getRequestMethod().equals("GET")) { send(x,405,"application/json","{\"error\":\"Method not allowed\"}"); return; }
    if (!staff(x)) { send(x,401,"application/json","{\"error\":\"Wrong PIN\"}"); return; }

    StringBuilder b=new StringBuilder("[");
    synchronized(orders) {
      for (String[] o:orders) {
        if (b.length()>1) b.append(",");
        b.append("{\"id\":").append(o[0])
         .append(",\"tok\":\"").append(j(o[1]))
         .append("\",\"nm\":\"").append(j(o[2]))
         .append("\",\"roll\":\"").append(j(o[3]))
         .append("\",\"ph\":\"").append(j(o[4]))
         .append("\",\"time\":\"").append(j(o[5]))
         .append("\",\"note\":\"").append(j(o[6]))
         .append("\",\"total\":").append(o[7])
         .append(",\"at\":").append(o[8])
         .append(",\"status\":\"").append(j(o[9]))
         .append("\",\"items\":[");
        String[] it=o[10].isEmpty()?new String[0]:o[10].split("\\|");
        for(int i=0;i<it.length;i++) b.append(i>0?",":"").append("\"").append(j(it[i])).append("\"");
        b.append("]}");
      }
    }
    send(x,200,"application/json",b.append("]").toString());
  }

  static void createOrder(HttpExchange x) throws IOException {
    Map<String,String> f=form(x);
    String nm=clean(f.get("nm"),60), roll=clean(f.get("roll"),30), ph=clean(f.get("ph"),10);
    String time=clean(f.get("time"),20), note=clean(f.get("note"),200);
    if(nm.isEmpty()||roll.isEmpty()){send(x,400,"application/json","{\"error\":\"Name and roll number are required\"}");return;}
    if(!ph.matches("\\d{10}")){send(x,400,"application/json","{\"error\":\"Enter a valid 10-digit phone number\"}");return;}

    int total=0; List<String> lines=new ArrayList<>();
    for(String part:clean(f.get("items"),3000).split("\\|")){
      String[] p=part.split("~",-1);
      if(p.length!=2||!MENU.containsKey(p[0])) continue;
      int q; try{q=Integer.parseInt(p[1].trim());}catch(Exception e){continue;}
      if(q<1||q>20)continue;
      total += MENU.get(p[0])*q;
      lines.add(p[0]+" × "+q);
    }
    if(lines.isEmpty()){send(x,400,"application/json","{\"error\":\"No valid items in order\"}");return;}

    synchronized(orders){
      int max=0;
      for(String[] o:orders) try{max=Math.max(max,Integer.parseInt(o[0]));}catch(Exception ignored){}
      int id=max+1;
      String tok="QB"+(1000+id);
      orders.add(0,new String[]{""+id,tok,nm,roll,ph,time,note,""+total,
        ""+System.currentTimeMillis(),"New",String.join("|",lines)});
      save();
      send(x,200,"application/json","{\"ok\":true,\"tok\":\""+tok+"\",\"total\":"+total+"}");
    }
  }

  static void trackOrder(HttpExchange x) throws IOException {
    if(cors(x)) return;
    if(!x.getRequestMethod().equals("GET")){send(x,405,"application/json","{\"error\":\"Method not allowed\"}");return;}
    String q=x.getRequestURI().getRawQuery();
    String tok=null;
    if(q!=null) for(String p:q.split("&")) {
      int i=p.indexOf('=');
      if(i>0 && URLDecoder.decode(p.substring(0,i),StandardCharsets.UTF_8).equals("token"))
        tok=URLDecoder.decode(p.substring(i+1),StandardCharsets.UTF_8);
    }
    if(tok==null||tok.isBlank()){send(x,400,"application/json","{\"error\":\"Token required\"}");return;}
    synchronized(orders){
      for(String[] o:orders) if(o[1].equalsIgnoreCase(tok)){
        send(x,200,"application/json","{\"tok\":\""+j(o[1])+"\",\"status\":\""+j(o[9])+"\",\"total\":"+o[7]+",\"name\":\""+j(o[2])+"\",\"items\":\""+j(o[10])+"\"}");
        return;
      }
    }
    send(x,404,"application/json","{\"error\":\"Order not found\"}");
  }

  static void status(HttpExchange x) throws IOException {
    if(cors(x)) return;
    if(!x.getRequestMethod().equals("POST")){send(x,405,"application/json","{\"error\":\"Method not allowed\"}");return;}
    if(!staff(x)){send(x,401,"application/json","{\"error\":\"Wrong PIN\"}");return;}
    Map<String,String> f=form(x); String st=f.getOrDefault("status",""), id=f.getOrDefault("id","");
    if(!STATUS.contains(st)){send(x,400,"application/json","{\"error\":\"Bad status\"}");return;}
    synchronized(orders){
      for(String[] o:orders) if(o[0].equals(id)){o[9]=st;save();send(x,200,"application/json","{\"ok\":true}");return;}
    }
    send(x,404,"application/json","{\"error\":\"Order not found\"}");
  }

  static void files(HttpExchange x)throws IOException{
    String p=x.getRequestURI().getPath();
    if(p.equals("/"))p="/index.html";
    Path f=PUBLIC.resolve("."+p).normalize();
    if(!f.startsWith(PUBLIC)||!Files.isRegularFile(f)){send(x,404,"text/plain","Not found");return;}
    securityHeaders(x);
    String n=f.getFileName().toString(),t="application/octet-stream";
    if(n.endsWith(".html"))t="text/html; charset=utf-8";
    else if(n.endsWith(".js"))t="text/javascript; charset=utf-8";
    else if(n.endsWith(".css"))t="text/css; charset=utf-8";
    else if(n.endsWith(".svg"))t="image/svg+xml";
    else if(n.endsWith(".png"))t="image/png";
    byte[] d=Files.readAllBytes(f);
    x.getResponseHeaders().set("Content-Type",t);x.sendResponseHeaders(200,d.length);
    try(OutputStream o=x.getResponseBody()){o.write(d);}
  }

  static boolean staff(HttpExchange x){return PIN.equals(x.getRequestHeaders().getFirst("X-Staff-Pin"));}

  static void securityHeaders(HttpExchange x){
    Headers h=x.getResponseHeaders();
    h.set("X-Content-Type-Options","nosniff");
    h.set("X-Frame-Options","DENY");
    h.set("Referrer-Policy","strict-origin-when-cross-origin");
    h.set("Content-Security-Policy","default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'");
  }

  static boolean cors(HttpExchange x)throws IOException{
    Headers h=x.getResponseHeaders();
    h.set("Access-Control-Allow-Origin","*");
    h.set("Access-Control-Allow-Headers","Content-Type, X-Staff-Pin");
    h.set("Access-Control-Allow-Methods","GET, POST, OPTIONS");
    if(x.getRequestMethod().equals("OPTIONS")){x.sendResponseHeaders(204,-1);x.close();return true;}
    return false;
  }

  static void send(HttpExchange x,int code,String type,String body)throws IOException{
    securityHeaders(x);
    byte[] d=body.getBytes(StandardCharsets.UTF_8);
    x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
    x.sendResponseHeaders(code,d.length);
    try(OutputStream o=x.getResponseBody()){o.write(d);}
  }

  static Map<String,String> form(HttpExchange x)throws IOException{
    byte[] raw=x.getRequestBody().readNBytes(30000); Map<String,String> m=new HashMap<>();
    for(String kv:new String(raw,StandardCharsets.UTF_8).split("&")){
      int i=kv.indexOf('=');
      if(i>0)m.put(URLDecoder.decode(kv.substring(0,i),StandardCharsets.UTF_8),
        URLDecoder.decode(kv.substring(i+1),StandardCharsets.UTF_8));
    } return m;
  }

  static String clean(String s,int max){
    if(s==null)return "";
    s=s.replaceAll("[\\t\\r\\n]+"," ").trim();
    return s.length()>max?s.substring(0,max):s;
  }
  static String j(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}

  static void save(){
    StringBuilder b=new StringBuilder();
    for(int i=orders.size()-1;i>=0;i--)b.append(String.join("\t",orders.get(i))).append("\n");
    try{Files.writeString(DATA,b.toString());}catch(IOException e){System.err.println("Save failed: "+e);}
  }
  static void load()throws IOException{
    if(!Files.exists(DATA))return;
    for(String l:Files.readAllLines(DATA)){
      String[] c=l.split("\t",-1);if(c.length==11)orders.add(0,c);
    }
  }
}