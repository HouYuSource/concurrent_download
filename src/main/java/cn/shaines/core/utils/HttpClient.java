package cn.shaines.core.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
使用方法:
HttpClient client = HttpClient.buildHttpClient();
Request request = client.buildRequest("http://localhost:8881/charge/testGet?key1=0111&value=shaines.cn");
// Request request = client.buildRequest("http://localhost:8881/charge/testPost").setMethod(Method.POST);
// Request request = client.buildRequest("http://localhost:8881/charge/testPut").setMethod(Method.PUT);
// Request request = client.buildRequest("http://localhost:8881/charge/testFile").setMethod(Method.POST);
request.setParam(Params.ofFormData().add("key1", "1111").add("key3", "2222")
                         .addFile("key2", new File("C:\\Users\\houyu\\Desktop\\1.txt"))
                         .addFile("key4", new File("C:\\Users\\houyu\\Desktop\\2.png")));
Response<String> response = request.execute(BodyHandlers.ofString());
System.out.println("response.getUrl() = " + response.getUrl());
System.out.println("response.getBody() = " + response.getBody());
 */

/**
 * @author houyu
 * @createTime 2019/10/11 22:31
 */
public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    /** 域对象 */
    protected Session session;

    private HttpClient(){}

    public static HttpClient buildHttpClient() {
        HttpClient client = new HttpClient();
        client.session = new Session();
        return client;
    }

    public Request buildRequest(String url) {
        return new Request(url, this);
    }

    public <T> Response<T> execute(Request request, Response.BodyHandler<T> bodyHandler) {
        return Executor.buildExecutor(request).execute(bodyHandler);
    }

    public Session getSession() {
        return session;
    }


    /** =============================================== Session ========================================================== */
    /**
     * @description 域对象
     * @date 2019-10-11 12:59:49
     * @author houyu for.houyu@foxmail.com
     */
    public static class Session {

        /** 请求方法 */
        private final Method method = Method.GET;
        /** 是否编码URL */
        private volatile boolean ifEncodeUrl = true;
        /** 是否缓存 */
        private volatile boolean ifCache = false;
        /** 超时时间 (单位:毫秒) 1分钟 */
        private volatile int timeout = 60000;
        /** 是否稳定重定向 */
        private volatile boolean ifStableRedirection = true;
        /** 是否处理https */
        private volatile boolean ifHandleHttps = true;
        /** 是否启用默认主机名验证程序 */
        private volatile boolean ifEnableDefaultHostnameVerifier = false;
        /** 推荐(上一个网页地址) */
        private volatile String referer;
        /** cookie */
        private volatile Map<String, String> cookie = new ConcurrentHashMap<>(16);
        /** 代理 */
        private volatile Proxy proxy;
        /** 参数编码 */
        private volatile Charset charset = Charset.forName("UTF-8");
        /** 主机名验证程序 */
        private volatile HostnameVerifier hostnameVerifier;
        /** SocketFactory */
        private volatile SSLSocketFactory sslSocketFactory;
        /** 携带参数(可使用于响应之后的操作) */
        private volatile Map<String, Object> extra = new ConcurrentHashMap<>(16);
        /** 请求头信息 (默认的请求头信息) */
        private volatile Map<String, Object> header = new ConcurrentHashMap<>(8);

        /* -------------------------------- constructor -------------------------- start */

        protected Session() {
            header.put("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8");
            header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.84 Safari/537.36 shaines.cn");
            header.put("Accept-Encoding", "gzip");
            header.put("Accept-Language", "zh-CN,zh;q=0.8");
            // header.put("Content-Type", "application/x-www-form-urlencoded");
            // header = Collections.unmodifiableMap(header);
            /** 初始化全局主机名验证程序 */
            hostnameVerifier = (s, sslSession) -> true;
            /** 初始化全局主机名验证程序 */
            X509TrustManager x509TrustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    // return new X509Certificate[0];
                    return null;
                }
            };
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { x509TrustManager }, new SecureRandom());
                sslSocketFactory = sslContext.getSocketFactory();
            } catch(NoSuchAlgorithmException | KeyManagementException e) {
                log.warn("init SSLContext has exception ", e);
            }
        }

        /* -------------------------------- constructor -------------------------- end */


        /* ---------------------------------- setter ----------------------------- start */

        public Session setIfEncodeUrl(boolean ifEncodeUrl) {
            this.ifEncodeUrl = ifEncodeUrl;
            return this;
        }

        public Session setIfCache(boolean ifCache) {
            this.ifCache = ifCache;
            return this;
        }

        public Session setTimeout(int timeout) {
            timeout = timeout < 0 ? this.timeout : timeout;
            this.timeout = timeout;
            return this;
        }

        public Session setIfStableRedirection(boolean ifStableRedirection) {
            this.ifStableRedirection = ifStableRedirection;
            return this;
        }

        public Session setIfHandleHttps(boolean ifHandleHttps) {
            this.ifHandleHttps = ifHandleHttps;
            return this;
        }

        public Session setIfEnableDefaultHostnameVerifier(boolean ifEnableDefaultHostnameVerifier) {
            this.ifEnableDefaultHostnameVerifier = ifEnableDefaultHostnameVerifier;
            return this;
        }

        public Session setReferer(String referer) {
            this.referer = Util.nullOfDefault(referer, this.referer);
            return this;
        }

        public Session addCookie(Map<String, String> cookie) {
            if(Util.isNotEmpty(cookie)) {
                cookie.forEach(this.cookie::put);
            }
            return this;
        }

        /**
         * 覆盖之前的所有 cookie
         */
        public Session setCookie(String cookie) {
            if(Util.isNotEmpty(cookie)) {
                String[] split = cookie.split(Constant.COOKIE_SPLIT);
                for(String cookieObject : split) {
                    String[] keyAndVal = cookieObject.split(Constant.EQU, 2);
                    this.cookie.put(keyAndVal[0], keyAndVal[1]);
                }
            }
            return this;
        }

        public Session setProxy(Proxy proxy) {
            this.proxy = Util.nullOfDefault(proxy, this.proxy);
            return this;
        }

        public Session setCharset(Charset charset) {
            this.charset = Util.nullOfDefault(charset, this.charset);
            return this;
        }

        public Session setHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = Util.nullOfDefault(hostnameVerifier, this.hostnameVerifier);
            return this;
        }

        public Session setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = Util.nullOfDefault(sslSocketFactory, this.sslSocketFactory);
            return this;
        }

        public Session addExtra(Map<String, Object> extra) {
            if(Util.isNotEmpty(extra)) {
                extra.forEach(this.extra::put);
            }
            return this;
        }

        public Session addExtra(String key, Object val) {
            if(Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                this.extra.put(key, val);
            }
            return this;
        }

        /**
         * 覆盖之前的所有 extra
         * @param extra 如果不为 null ,那就覆盖之前所有的 extra
         */
        public Session setExtra(Map<String, Object> extra) {
            this.extra = Util.nullOfDefault(extra, this.extra);
            return this;
        }

        public Session addHeader(Map<String, Object> header) {
            if(Util.isNotEmpty(header)) {
                header.forEach(this.header::put);
            }
            return this;
        }

        public Session addHeader(String key, Object val) {
            if(Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                this.header.put(key, val);
            }
            return this;
        }

        /**
         * 覆盖之前的所有 header
         * @param header 如果不为 null ,那就覆盖之前所有的 header
         */
        public Session setHeader(Map<String, Object> header) {
            this.header = Util.nullOfDefault(header, this.header);
            return this;
        }

        /* ---------------------------------- setter ----------------------------- end */

        /* ---------------------------------- getter ----------------------------- start */

        protected Method getMethod() {
            return method;
        }

        protected boolean getIfEncodeUrl() {
            return ifEncodeUrl;
        }

        protected boolean getIfCache() {
            return ifCache;
        }

        protected int getTimeout() {
            return timeout;
        }

        protected boolean getIfStableRedirection() {
            return ifStableRedirection;
        }

        protected boolean getIfHandleHttps() {
            return ifHandleHttps;
        }

        protected boolean getIfEnableDefaultHostnameVerifier() {
            return ifEnableDefaultHostnameVerifier;
        }

        protected String getReferer() {
            return referer;
        }

        protected String getCookie() {
            /* cookie ex:key2=val2; key1=val1 */
            StringBuilder builder = new StringBuilder(128);
            for(Entry<String, String> entry : cookie.entrySet()) {
                builder.append(entry.getKey()).append("=").append(entry.getValue()).append("; ");
            }
            return builder.length() > 0 ? builder.delete(builder.length() - 2, builder.length()).toString() : "";
        }

        protected Proxy getProxy() {
            return proxy;
        }

        protected Charset getCharset() {
            return charset;
        }

        protected HostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }

        protected SSLSocketFactory getSslSocketFactory() {
            return sslSocketFactory;
        }

        protected Map<String, Object> getExtra() {
            return new HashMap<>(extra);
        }

        protected Map<String, Object> getHeader() {
            return new HashMap<>(header);
        }

        /* ---------------------------------- getter ----------------------------- end */

        /* ---------------------------------- toString ----------------------------- start */

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Session{");
            sb.append("method=").append(method);
            sb.append(", ifEncodeUrl=").append(ifEncodeUrl);
            sb.append(", ifCache=").append(ifCache);
            sb.append(", timeout=").append(timeout);
            sb.append(", ifStableRedirection=").append(ifStableRedirection);
            sb.append(", ifHandleHttps=").append(ifHandleHttps);
            sb.append(", ifEnableDefaultHostnameVerifier=").append(ifEnableDefaultHostnameVerifier);
            sb.append(", referer='").append(referer).append('\'');
            sb.append(", cookie=").append(cookie);
            sb.append(", proxy=").append(proxy);
            sb.append(", charset=").append(charset);
            sb.append(", hostnameVerifier=").append(hostnameVerifier);
            sb.append(", sslSocketFactory=").append(sslSocketFactory);
            sb.append(", extra=").append(extra);
            sb.append(", header=").append(header);
            sb.append('}');
            return sb.toString();
        }

        /* ---------------------------------- toString ----------------------------- end */

    }

    /** =============================================== Session ========================================================== */

    /* ---------------------------------------------- toString ---------------------------------------------- start */

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HttpClient{");
        sb.append("session=").append(session);
        sb.append('}');
        return sb.toString();
    }

    /* ---------------------------------------------- toString ---------------------------------------------- start */

    /** =============================================== Executor ========================================================== */
    /**
     * 执行对象
     */
    public static class Executor {

        private static final Logger logger = LoggerFactory.getLogger(Executor.class);

        /** 请求对象 */
        protected Request request;
        /** 重定向的url列表 */
        protected List<String> redirectUrlList;
        /** HttpURLConnection对象 */
        protected HttpURLConnection http;

        protected Executor(Request request) {
            this.request = request;
        }
        protected static Executor buildExecutor(Request request) {
            return new Executor(request);
        }

        protected <T> Response<T> execute(Response.BodyHandler<T> handler) {
            Response<T> response;
            try {
                response = handleHttpConnection(handler);
            } catch(Exception e) {
                logger.warn("handleHttpConnection has exception", e);
                response = (Response<T>) Response.getErrorResponse(this.request);
            }
            return response;
        }

        private <T> Response<T> handleHttpConnection(Response.BodyHandler<T> handler) {
            // 处理URL参数问题
            this.handleUrlParam();
            // 初始化连接
            this.initConnection();
            // 发送数据包裹
            this.send();
            // 处理重定向
            boolean ifRedirect = this.handleRedirect();
            if(ifRedirect) {
                // 递归实现重定向
                return this.handleHttpConnection(handler);
            }
            // 返回响应
            return new Response<>(this, handler);
        }

        private boolean handleRedirect() {
            if(this.request.getIfStableRedirection()) {
                // 采用稳定重定向方式, 需要处理重定向问题
                int responseCode;
                try {
                    responseCode = this.http.getResponseCode();
                } catch(IOException var3) {
                    logger.warn("{} get response code has exception", this.request.getUrl());
                    responseCode = 400;
                }
                if(responseCode == Constant.REDIRECT_CODE_301
                        || responseCode == Constant.REDIRECT_CODE_302
                        || responseCode == Constant.REDIRECT_CODE_303) {
                    this.request.setUrl(this.http.getHeaderField(Constant.LOCATION));
                    this.redirectUrlList = Util.nullOfDefault(this.redirectUrlList, new ArrayList<String>(8));
                    this.redirectUrlList.add(this.request.getUrl());
                    if(this.redirectUrlList.size() < 8) {
                        // 断开本次连接, 然后重新请求
                        this.http.disconnect();
                        logger.debug("{} request redirecting ", this.request.getUrl());
                        return true;
                    }
                }
            } else {
                // 使用默认的重定向规则处理, 无序手动处理, 但是有可能出现重定向失败
                // do non thing
            }
            return false;
        }


        /** 发送数据 */
        private void send() {
            try {
                if(Method.GET.equals(this.request.getMethod())) {
                    this.http.connect();
                } else {
                    // POST...
                    this.handleContentTypeAndBody();
                }
            } catch(IOException e) {
                logger.warn("{} send data has exception", this.request.getUrl());
                throw new RuntimeException(e);
            }
        }

        /** 处理 ContentType 和 传输内容 */
        private void handleContentTypeAndBody() throws IOException {
            if(!Method.GET.equals(this.request.getMethod())) {
                // non GET
                /* handle ContentType 有可能多个content-type, 大小写不一致的问题 */
                if(this.request.getParam() == null) {
                    this.request.setParam(Request.Params.ofForm());
                }
                this.request.removeHeader("content-type");
                Object tempContentType = this.request.getHeader(Constant.CONTENT_TYPE);
                String contentType = tempContentType == null ?  this.request.getParam().contentType : String.valueOf(tempContentType);
                this.addAndRefreshHead(Constant.CONTENT_TYPE, contentType);
                /* handle body */
                // 非GET 所有的请求头必须在调用getOutputStream()之前设置好, 这里相当于GET的connect();
                byte[] body = this.request.getParam().ok().body;
                if(Util.isNotEmpty(body)) {
                    try(OutputStream outputStream = this.http.getOutputStream()) {
                        // 使用 try-with-resource 方式处理流, 无需手动关闭流操作
                        outputStream.write(body);
                        outputStream.flush();
                    }
                }
            }
        }

        /** 刷新 请求头信息 */
        private void addAndRefreshHead(String key, Object value) {
            if (Util.isNotEmpty(key) && Util.isNotEmpty(value)) {
                this.request.addHeader(key, value);
                this.http.setRequestProperty(key, String.valueOf(value));
            }
        }

        /** 初始化连接 */
        private void initConnection() throws RuntimeException {
            URL url;
            try {
                url = new URL(this.request.getUrl());
            } catch(MalformedURLException e) {
                logger.warn("{} create URL has exception", this.request.getUrl());
                throw new RuntimeException("创建URL出错" + e.getMessage());
            }
            //
            try {
                this.http = this.openConnection(url, this.request.getProxy());
                //
                if(this.request.getTimeout() > 0) {
                    // 设置超时
                    this.http.setConnectTimeout(this.request.getTimeout());
                    this.http.setReadTimeout(this.request.getTimeout());
                }
                // 设置请求方法
                this.http.setRequestMethod(this.request.getMethod().name());
            } catch(IOException e) {
                logger.warn("{} open connection has exception", this.request.getUrl());
                throw new RuntimeException("打开连接出错" + e.getMessage());
            }
            //
            this.http.setDoInput(true);
            if(!Method.GET.equals(this.request.getMethod())) {
                // 非GET方法需要设置可输入
                http.setDoOutput(true);
                http.setUseCaches(false);
            }
            // 设置cookie
            this.setCookie();
            // 设置请求头到连接中
            this.request.getHeader().forEach((k, v) -> this.http.setRequestProperty(k, String.valueOf(v)));
            // 设置缓存
            if(this.request.getIfCache()) {
                this.http.setUseCaches(true);
            }
            // 设置是否自动重定向
            this.http.setInstanceFollowRedirects(!(this.request.getIfStableRedirection()));
        }

        private void setCookie() {
            if(Util.isNotEmpty(this.request.getCookie())) {
                logger.debug("{} set cookie {}", this.request.getUrl(), this.request.getCookie());
                this.request.removeHeader("cookie");
                this.request.addHeader(Constant.REQUEST_COOKIE, this.request.getCookie());
            }
        }

        /** 打开连接 */
        private HttpURLConnection openConnection(URL url, Proxy proxy) throws IOException {
            URLConnection connection;
            if(this.request.getProxy() == null) {
                connection = url.openConnection();
            } else if(Util.isNotEmpty(proxy.getUsername())) {
                // 设置代理服务器
                java.net.Proxy javaNetProxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort()));
                connection = url.openConnection(javaNetProxy);
                String authString = proxy.getUsername() + ":" + proxy.getPassword();
                String auth = "Basic " + Base64.getEncoder().encodeToString(authString.getBytes(this.request.getClient().session.getCharset()));
                connection.setRequestProperty(Constant.PROXY_AUTHORIZATION, auth);
                logger.debug("{} do proxy server ", this.request.getUrl());
            } else if(Util.isNotEmpty(proxy.getHost())) {
                // 设置代理主机和端口
                java.net.Proxy javaNetProxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort()));
                connection = url.openConnection(javaNetProxy);
                logger.debug("{} do proxy ", this.request.getUrl());
            } else {
                // 不设置代理
                connection = url.openConnection();
            }
            if(this.request.getIfHandleHttps() && connection instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) connection;
                // 设置主机名验证程序
                if (this.request.getIfEnableDefaultHostnameVerifier()) {
                    httpsConn.setHostnameVerifier(this.request.getHostnameVerifier());
                }
                // 设置ssl factory
                httpsConn.setSSLSocketFactory(this.request.getSslSocketFactory());
            }
            return (HttpURLConnection) connection;
        }


        /**
         * 设置 url 参数问题
         */
        private void handleUrlParam() {
            // 处理url中的query进行url编码
            int indexOf;
            if (this.request.getIfEncodeUrl() && (indexOf = this.request.getUrl().indexOf(Constant.queryFlag)) > -1) {
                String query = this.request.getUrl().substring(indexOf);
                query = Util.urlEncode(query, request.getClient().session.getCharset());
                query = query.replace("%3F", "?").replace("%2F", "/").replace("%3A", ":").replace("%3D", "=").replace("%26", "&").replace("%23", "#");
                this.request.setUrl(this.request.getUrl().substring(0, indexOf) + query);
            }
        }

    }

    /** =============================================== Executor ========================================================== */


    /** =============================================== Request ========================================================== */
    /**
     * 请求对象
     */
    public static class Request {

        /** 请求网站地址 */
        private String url;
        /** 请求方法 */
        private Method method;
        /** 请求头 */
        private Map<String, Object> header;
        /** 请求参数 */
        private Param param;
        /** 携带参数(可使用于响应之后的操作) */
        private Map<String, Object> extra;
        /** 代理 */
        private Proxy proxy;
        /** 是否编码URL */
        private boolean ifEncodeUrl;
        /** 是否缓存 */
        private boolean ifCache;
        /** 连接超时(单位:毫秒) */
        private int timeout;
        /** 携带cookie(优先) ex: key1=val1; key2=val2 */
        private String cookie;
        /** 是否稳定重定向 */
        private boolean ifStableRedirection;
        /** 是否处理https */
        private boolean ifHandleHttps;
        /** 是否启用默认主机名验证程序 */
        private boolean ifEnableDefaultHostnameVerifier;
        /** 主机名验证程序 */
        private volatile HostnameVerifier hostnameVerifier;
        /** SocketFactory */
        private volatile SSLSocketFactory sslSocketFactory;
        /** 客户端 */
        private HttpClient client;

        protected Request(String url, HttpClient client) {
            this.setUrl(url);
            this.client = client;
            this.init();
        }

        private void init() {
            Session session = client.getSession();
            this.setMethod(session.getMethod());
            this.setHeader(session.getHeader());
            if (Util.isNotEmpty(session.getReferer())) {
                this.addHeader(Constant.REFERER, session.getReferer());
            }
            this.setExtra(session.getExtra());
            this.setProxy(session.getProxy());
            this.setIfEncodeUrl(session.getIfEncodeUrl());
            this.setIfCache(session.getIfCache());
            this.setTimeout(session.getTimeout());
            this.setCookie(session.getCookie());
            this.setIfStableRedirection(session.getIfStableRedirection());
            this.setIfHandleHttps(session.getIfHandleHttps());
            this.setIfEnableDefaultHostnameVerifier(session.getIfEnableDefaultHostnameVerifier());
            this.setHostnameVerifier(session.getHostnameVerifier());
            this.setSslSocketFactory(session.getSslSocketFactory());
        }

        public <T> Response<T> execute(Response.BodyHandler<T> bodyHandler) {
            return this.client.execute(this, bodyHandler);
        }

        /* ---------------------------- setter ---------------------------------- start */

        protected Request setUrl(String url) {
            // 校验url地址
            this.url = String.valueOf(url);
            if(!this.url.toLowerCase().startsWith(Constant.HTTP)) {
                this.url = Constant.HTTP + "://" + this.url;
            }
            return this;
        }

        public Request setMethod(Method method) {
            this.method = Util.nullOfDefault(method, Method.GET);
            return this;
        }

        public Request GET() {
            return this.setMethod(Method.GET);
        }

        public Request POST() {
            return this.setMethod(Method.POST);
        }

        public Request PUT() {
            return this.setMethod(Method.PUT);
        }

        public Request DELETE() {
            return this.setMethod(Method.DELETE);
        }

        /**
         * 调用这个方法会覆盖之前所有的请求头
         * @param header 如果 header 不为 null ,那就覆盖之前的所有 header
         */
        public Request setHeader(Map<String, Object> header) {
            this.header = Util.nullOfDefault(header, this.header);
            return this;
        }

        public Request addHeader(String key, Object val) {
            if (Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                // 这里 header 不可以能为 null
                this.header.put(key, val);
            }
            return this;
        }

        protected Request removeHeader(String key) {
            if (Util.isNotEmpty(key)) {
                // 这里 header 不可以能为 null
                this.header.remove(key);
            }
            return this;
        }

        public Request setParam(Param param) {
            this.param = Util.nullOfDefault(param, this.param);
            return this;
        }

        /**
         * 调用这个方法会覆盖之前所有的extra
         * @param extra 如果 extra 不为 null ,那就覆盖之前的所有 extra
         */
        public Request setExtra(Map<String, Object> extra) {
            this.extra = Util.nullOfDefault(extra, this.extra);
            return this;
        }

        public Request addExtra(String key, Object val) {
            if (Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                // 这里 extra 不可以能为 null
                this.extra.put(key, val);
            }
            return this;
        }

        public Request setProxy(Proxy proxy) {
            this.proxy = Util.nullOfDefault(proxy, this.proxy);
            return this;
        }

        public Request setIfEncodeUrl(boolean ifEncodeUrl) {
            this.ifEncodeUrl = ifEncodeUrl;
            return this;
        }

        public Request setIfCache(boolean ifCache) {
            this.ifCache = ifCache;
            return this;
        }

        public Request setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Request setCookie(String cookie) {
            this.cookie = Util.emptyOfDefault(cookie, this.cookie);
            return this;
        }

        public Request setIfStableRedirection(boolean ifStableRedirection) {
            this.ifStableRedirection = ifStableRedirection;
            return this;
        }

        public Request setIfHandleHttps(boolean ifHandleHttps) {
            this.ifHandleHttps = ifHandleHttps;
            return this;
        }

        public Request setIfEnableDefaultHostnameVerifier(boolean ifEnableDefaultHostnameVerifier) {
            this.ifEnableDefaultHostnameVerifier = ifEnableDefaultHostnameVerifier;
            return this;
        }

        public Request setHostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = Util.nullOfDefault(hostnameVerifier, this.hostnameVerifier);
            return this;
        }

        public Request setSslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = Util.nullOfDefault(sslSocketFactory, this.sslSocketFactory);
            return this;
        }

        /* ---------------------------- setter ---------------------------------- end */

        /* ---------------------------- getter ---------------------------------- start */

        protected Map<String, Object> getExtra() {
            return extra;
        }

        protected Proxy getProxy() {
            return proxy;
        }

        protected String getCookie() {
            return cookie;
        }

        protected String getUrl() {
            return url;
        }

        protected Method getMethod() {
            return method;
        }

        protected Map<String, Object> getHeader() {
            return header;
        }

        protected Object getHeader(String key) {
            return Util.isNotEmpty(key) ? this.header.get(key) : null;
        }

        protected Param getParam() {
            return param;
        }

        protected boolean getIfEncodeUrl() {
            return ifEncodeUrl;
        }

        protected boolean getIfCache() {
            return ifCache;
        }

        protected int getTimeout() {
            return timeout;
        }

        protected boolean getIfStableRedirection() {
            return ifStableRedirection;
        }

        protected boolean getIfHandleHttps() {
            return ifHandleHttps;
        }

        protected boolean getIfEnableDefaultHostnameVerifier() {
            return ifEnableDefaultHostnameVerifier;
        }

        protected HostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }

        protected SSLSocketFactory getSslSocketFactory() {
            return sslSocketFactory;
        }

        protected HttpClient getClient() {
            return client;
        }

        /* ---------------------------- getter ---------------------------------- end */


        /* ---------------------------- inner class ---------------------------------- start */

        public static abstract class Param {
            String contentType;
            byte[] body;

            Param() {}
            Param(String contentType, byte[] body) {
                this.contentType = contentType;
                this.body = body;
            }

            /**
             * 获取 param 之前会先调用 ok() 方法, 确保准备完毕
             */
            public abstract Param ok();

            @Override
            public String toString() {
                final StringBuilder sb = new StringBuilder("BaseParam{");
                sb.append("contentType='").append(contentType).append('\'');
                sb.append(", body=").append(new String(body));
                sb.append('}');
                return sb.toString();
            }
        }

        public static abstract class Params {

            public static ParamJson ofJson(String jsonString, Charset charset) {
                return new ParamJson(jsonString, charset);
            }
            public static ParamJson ofJson(String jsonString) {
                return ofJson(jsonString, Constant.defaultCharset);
            }

            public static ParamForm ofForm(Charset charset) {
                return new ParamForm(charset);
            }
            public static ParamForm ofForm() {
                return ofForm(Constant.defaultCharset);
            }

            public static ParamFormData ofFormData(Charset charset) {
                return new ParamFormData(charset);
            }
            public static ParamFormData ofFormData() {
                return ofFormData(Constant.defaultCharset);
            }

            public static class ParamJson extends Param {
                ParamJson(String jsonString, Charset charset) {
                    super(Constant.CONTENT_TYPE_WITH_JSON + charset.name(), jsonString.getBytes(charset));
                }

                @Override
                public Param ok() {
                    return this;
                }
            }

            public static class ParamForm extends Param {
                Map<String, Object> paramMap;
                Charset charset;
                ParamForm(Charset charset) {
                    this.charset = charset;
                    this.paramMap = new HashMap<>(8);
                }
                public ParamForm add(String key, Object val) {
                    if (Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                        this.paramMap.put(key, val);
                    }
                    return this;
                }
                public ParamForm add(Map<String, Object> paramMap) {
                    if (Util.isNotEmpty(paramMap)) {
                        paramMap.forEach(this::add);
                    }
                    return this;
                }
                @Override
                public Param ok() {
                    this.contentType = Constant.CONTENT_TYPE_WITH_FORM + this.charset.name();
                    this.body = Util.paramMapAsString(this.paramMap, this.charset).getBytes(this.charset);
                    return this;
                }
            }

            public static class ParamFormData extends Param {
                @Override
                public Param ok() {
                    this.body = this.fillData();
                    return this;
                }

                @Override
                public String toString() {
                    final StringBuilder sb = new StringBuilder("ParamFormData{").append("\n");
                    sb.append("contentType='").append("\n").append(contentType).append('\'').append("\n");
                    sb.append("body=").append("\n").append(new String(this.body, this.charset)).append("\n");
                    sb.append("charset=").append("\n").append(charset).append("\n");
                    sb.append('}');
                    return sb.toString();
                }

                /* ------------------------------------------------------------------------------------------- */

                public static class Resource {
                    public File file;
                    public Charset charset;

                    public Resource(File file, Charset charset) {
                        this.file = file;
                        this.charset = Util.nullOfDefault(charset, Constant.defaultCharset);
                    }

                    public File getFile() {
                        return file;
                    }

                    public Charset getCharset() {
                        return charset;
                    }
                }

                public static final String horizontalLine = "--------------------------";
                public static final String lineFeed = System.lineSeparator();
                public static final String fileFormat = "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\nContent-Type: %s";
                public static final String textFormat = "Content-Disposition: form-data; name=\"%s\"";

                Charset charset;
                String separator;
                String endFlag;
                Map<String, Object> tempMap;
                protected ParamFormData(Charset charset) {
                    this.charset = charset;
                    init();
                }
                private void init() {
                    long randomNumber = ThreadLocalRandom.current().nextLong();
                    contentType = Constant.CONTENT_TYPE_WITH_FORM_DATA + horizontalLine + randomNumber;
                    separator = "--" + horizontalLine + randomNumber;
                    endFlag = separator + "--" + lineFeed;
                    tempMap = new LinkedHashMap<>(8);
                }

                public ParamFormData add(String key, Object val) {
                    if(Util.isNotEmpty(key) && Util.isNotEmpty(val)) {
                        tempMap.put(key, val);
                    }
                    return this;
                }
                public ParamFormData addFile(String key, File file) {
                    return this.addFile(key, file, null);
                }

                public ParamFormData addFile(String key, File file, Charset charset) {
                    if(Util.isNotEmpty(key) && file != null) {
                        this.add(key, new Resource(file, charset));
                    }
                    return this;
                }

                private byte[] fillData() {
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        for(Entry<String, Object> entry : tempMap.entrySet()) {
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            if(value instanceof Resource) {
                                this.appendResource(outputStream, key, (Resource) value);
                            } else {
                                this.appendText(outputStream, key, value);
                            }
                        }
                        outputStream.write(endFlag.getBytes(this.charset));
                        outputStream.flush();
                        return outputStream.toByteArray();
                    } catch(IOException e) {
                        e.printStackTrace();
                        return new byte[0];
                    }
                }

                private void appendResource(OutputStream outputStream, String key, Resource value) {
                    StringBuilder builder = new StringBuilder(1024);
                    File file = value.getFile();
                    Path path = Paths.get(file.getAbsolutePath());
                    try {
                        // append 头部信息
                        builder.append(separator).append(lineFeed);
                        builder.append(String.format(fileFormat, key, file.getName(), this.parseFileType(path))).append(lineFeed);
                        builder.append(lineFeed);
                        outputStream.write(builder.toString().getBytes(value.getCharset()));
                        // append 实体
                        Files.copy(path, outputStream);
                        // append 换行
                        outputStream.write(lineFeed.getBytes(this.charset));
                        outputStream.flush();
                    } catch(IOException e) {
                        // do non thing
                    }
                }

                private void appendText(OutputStream outputStream, String key, Object value) {
                    StringBuilder builder = new StringBuilder(1024);
                    try {
                        // append 头部信息
                        builder.append(separator).append(lineFeed);
                        builder.append(String.format(textFormat, key)).append(lineFeed);
                        builder.append(lineFeed);
                        // append 实体
                        builder.append(value);
                        outputStream.write(builder.toString().getBytes(this.charset));
                        // append 换行
                        outputStream.write(lineFeed.getBytes(this.charset));
                        outputStream.flush();
                    } catch(IOException e) {
                        // do non thing
                    }
                }

                private String parseFileType(Path path) throws IOException {
                    return Files.probeContentType(path);
                }
            }
        }

        /* ---------------------------- inner class ---------------------------------- end */

        /* ---------------------------- toString ---------------------------------- start */

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Request{");
            sb.append("url='").append(url).append('\'');
            sb.append(", method=").append(method);
            sb.append(", header=").append(header);
            sb.append(", param=").append(param);
            sb.append(", extra=").append(extra);
            sb.append(", proxy=").append(proxy);
            sb.append(", ifEncodeUrl=").append(ifEncodeUrl);
            sb.append(", ifCache=").append(ifCache);
            sb.append(", timeout=").append(timeout);
            sb.append(", cookie='").append(cookie).append('\'');
            sb.append(", ifStableRedirection=").append(ifStableRedirection);
            sb.append(", ifHandleHttps=").append(ifHandleHttps);
            sb.append(", ifEnableDefaultHostnameVerifier=").append(ifEnableDefaultHostnameVerifier);
            sb.append(", hostnameVerifier=").append(hostnameVerifier);
            sb.append(", sslSocketFactory=").append(sslSocketFactory);
            sb.append(", client=").append(client);
            sb.append('}');
            return sb.toString();
        }

        /* ---------------------------- toString ---------------------------------- start */

    }

    /** =============================================== Request ========================================================== */


    /** =============================================== Response ========================================================== */
    /**
     * 响应对象
     */
    public static class Response<T> {

        private static final Logger logger = LoggerFactory.getLogger(Response.class);

        /** 执行者 */
        private Executor executor;
        /** 响应处理 */
        private BodyHandler<T> bodyHandler;

        /** HttpURLConnection */
        private HttpURLConnection http;
        /** 请求url */
        private String url;
        /** 重定向的url列表 */
        private List<String> redirectUrlList;
        /** http响应状态码(HttpURLConnection.HTTP_OK) */
        private int statusCode;
        /** 响应头信息 */
        private Map<String, List<String>> header;
        /** cookie ex:key2=val2; key1=val1 */
        private Map<String, String> cookie;
        /** 携带参数(可使用于响应之后的操作) */
        private Map<String, Object> extra;
        /** 响应体 */
        private T body;

        private Response() {}
        protected Response(Executor executor, BodyHandler<T> bodyHandler) {
            this.executor = executor;
            this.bodyHandler = bodyHandler;
            this.init();
        }

        protected static Response<Object> getErrorResponse(Request request) {
            logger.debug("use errorResponse ...");
            Response<Object> errorResponse = new Response<>();
            errorResponse.http = null;
            errorResponse.url = request.getUrl();
            errorResponse.redirectUrlList = Collections.emptyList();
            errorResponse.statusCode = 400;
            errorResponse.header = Collections.emptyMap();
            errorResponse.cookie = Collections.emptyMap();
            errorResponse.extra = new HashMap<>(request.getExtra());
            errorResponse.body = null;
            return errorResponse;
        }

        private void init() {
            try {
                this.http = this.executor.http;
                this.url = this.executor.request.getUrl();
                this.redirectUrlList = this.executor.redirectUrlList;
                this.statusCode = this.executor.http.getResponseCode();
                this.header = this.executor.http.getHeaderFields();
                this.cookie = this.parseCookieAsMap();
                this.extra = new HashMap<>(this.executor.request.getExtra());
                this.handleHttpClientSession();
                if (this.bodyHandler != null) {
                    this.body = this.bodyHandler.accept(this.executor.request, this.http);
                }
            } catch(IOException e) {
                logger.warn("init HttpResponse has exception", e);
            } finally {
                this.http.disconnect();
            }
        }

        private void handleHttpClientSession() {
            HttpClient.Session session = this.executor.request.getClient().getSession();
            session.setReferer(this.url);
            session.addCookie(this.cookie);
            session.addExtra(this.extra);
        }

        /** 获取 cookieMap */
        private Map<String, String> parseCookieAsMap() {
            List<String> cookieList = this.header.get(Constant.RESPONSE_COOKIE);
            Map<String, String> cookieMap = Collections.emptyMap();
            if(Util.isNotEmpty(cookieList)) {
                cookieMap = new HashMap<>(cookieList.size());
                if(Util.isNotEmpty(cookieList)) {
                    for(String cookieObj : cookieList) {
                        String[] split = cookieObj.split(Constant.COOKIE_SPLIT);
                        if (split.length > 0) {
                            String[] keyAndVal = split[0].split(Constant.EQU, 2);
                            cookieMap.put(keyAndVal[0], keyAndVal[1]);
                        }
                    }
                }
            }
            return cookieMap;
        }

        /* ---------------------------------------------- getter ---------------------------------------------- start */

        public String getUrl() {
            return url;
        }

        public List<String> getRedirectUrlList() {
            return redirectUrlList;
        }

        public HttpURLConnection getHttp() {
            return http;
        }

        public Integer getStatusCode() {
            return statusCode;
        }

        public Map<String, List<String>> getHeader() {
            return header;
        }

        public Map<String, String> getCookie() {
            return cookie;
        }

        public T getBody() {
            return body;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        /* ---------------------------------------------- getter ---------------------------------------------- end */

        /* ---------------------------------------------- interface ---------------------------------------------- end */

        public interface BodyHandler<T> {
            /**
             * 回调接口
             * @param request Request 对象
             * @param http HttpURLConnection 对象
             * @return
             * @throws IOException
             */
            T accept(Request request, HttpURLConnection http) throws IOException;
        }

        public static abstract class BodyHandlers {

            /**
             * 回调 byte[] 接口
             */
            public interface CallbackByteArray {
                /**
                 * 回调 byte[] 方法
                 * @param data byte[] 对象
                 * @param index byte[] 的开始下标( 通常是0 )
                 * @param length byte[] 结束下标
                 */
                void accept(byte[] data, int index, int length) throws IOException;
            }

            private static BodyHandler<InputStream> ofInputStream() {
                return (request, http) -> {
                    InputStream inputStream = http.getResponseCode() < 400 ? http.getInputStream() : http.getErrorStream();
                    // 获取响应头是否有Content-Encoding=gzip
                    String gzip = http.getHeaderField(Constant.CONTENT_ENCODING);
                    if(Util.isNotEmpty(gzip) && gzip.contains(Constant.GZIP)) {
                        inputStream = new GZIPInputStream(inputStream);
                    }
                    return inputStream;
                };
            }

            public static BodyHandler<Void> ofCallbackByteArray(CallbackByteArray callback) {
                return (request, http) -> {
                    try(InputStream inputStream = ofInputStream().accept(request, http)) {
                        byte[] bytes = new byte[1024 * 3];
                        for (int i; (i = inputStream.read(bytes)) > -1; ) {
                            callback.accept(bytes, 0, i);
                        }
                        return null;
                    }
                };
            }

            public static BodyHandler<byte[]> ofByteArray() {
                return (request, http) -> {
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        ofCallbackByteArray((data, index, length) -> {
                            outputStream.write(data, index, length);
                            outputStream.flush();
                        }).accept(request, http);
                        return outputStream.toByteArray();
                    }
                };
            }

            public static BodyHandler<String> ofString(Charset... charset) {
                return (request, http) -> {
                    byte[] body = ofByteArray().accept(request, http);
                    Charset currentCharset = charset.length > 0 ? charset[0] : Constant.defaultCharset;
                    return new String(body, currentCharset);
                };
            }

            public static BodyHandler<Path> ofFile(Path path) {
                return (request, http) -> {
                    try(OutputStream outputStream = new FileOutputStream(path.toFile())) {
                        ofCallbackByteArray(outputStream::write).accept(request, http);
                        return path;
                    }
                };
            }
        }

        /* ---------------------------------------------- interface ---------------------------------------------- end */


        /* ---------------------------------------------- toString ---------------------------------------------- start */

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Response{");
            sb.append("executor=").append(executor);
            sb.append(", bodyHandler=").append(bodyHandler);
            sb.append(", http=").append(http);
            sb.append(", url='").append(url).append('\'');
            sb.append(", redirectUrlList=").append(redirectUrlList);
            sb.append(", statusCode=").append(statusCode);
            sb.append(", header=").append(header);
            sb.append(", cookie=").append(cookie);
            sb.append(", extra=").append(extra);
            sb.append(", body=").append(body);
            sb.append('}');
            return sb.toString();
        }

        /* ---------------------------------------------- toString ---------------------------------------------- end */
    }

    /** =============================================== Response ========================================================== */


    /** =============================================== Constant ========================================================== */

    /**
     * 常量
     */
    public interface Constant {

        String CONTENT_LENGTH = "Content-Length";
        String CONTENT_TYPE = "Content-Type";
        /** 获取响应的COOKIE */
        String RESPONSE_COOKIE = "Set-Cookie";
        /** 设置发送的COOKIE */
        String REQUEST_COOKIE = "Cookie";
        String REFERER = "Referer";
        String PROXY_AUTHORIZATION = "Proxy-Authorization";
        String CONTENT_ENCODING = "Content-Encoding";
        String LOCATION = "Location";

        String CONTENT_TYPE_WITH_FORM = "application/x-www-form-urlencoded; charset=";
        String CONTENT_TYPE_WITH_FORM_DATA = "multipart/form-data; boundary=";
        String CONTENT_TYPE_WITH_JSON = "application/json; charset=";
        String GZIP = "gzip";

        int REDIRECT_CODE_301 = 301;
        int REDIRECT_CODE_302 = 302;
        int REDIRECT_CODE_303 = 303;

        String COOKIE_SPLIT = "; ";
        String EQU = "=";
        String HTTP = "http";
        String AND_SIGN = "&";
        String queryFlag = "?";

        Charset defaultCharset = Charset.defaultCharset();
    }

    /** =============================================== Constant ========================================================== */


    /** =============================================== Method ========================================================== */
    /**
     * 请求方法
     */
    public enum Method {
        /**  */
        GET,
        /**  */
        POST,
        /**  */
        PUT,
        /**  */
        DELETE
    }
    /** =============================================== Method ========================================================== */


    /** =============================================== Proxy ========================================================== */
    /**
     * 代理对象
     */
    public static class Proxy {

        private String host;
        private Integer port;
        private String username;
        private String password;

        protected Proxy(String host, Integer port) {
            this(host, port, null, null);
        }

        protected Proxy(String host, Integer port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Proxy{");
            sb.append("host='").append(host).append('\'');
            sb.append(", port=").append(port);
            sb.append(", username='").append(username).append('\'');
            sb.append(", password='").append(password).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static abstract class Proxys {
        public static Proxy of(String host, Integer port) {
            return of(host, port, null, null);
        }
        public static Proxy of(String host, Integer port, String username, String password) {
            return new Proxy(host, port, username, password);
        }
    }

    /** =============================================== Proxy ========================================================== */


    /** =============================================== Util ========================================================== */
    /**
     * 工具类
     */
    public static abstract class Util {

        public static boolean isEmpty(Object o) {
            if(o == null) {
                return true;
            }
            if(o instanceof String) {
                return ((String) o).isEmpty();
            } else if(o instanceof Collection) {
                return ((Collection) o).isEmpty();
            } else if(o instanceof Map) {
                return ((Map) o).isEmpty();
            } else if(o instanceof Object[]) {
                return ((Object[]) o).length == 0;
            } else {
                return false;
            }
        }

        public static boolean isNotEmpty(Object o) {
            return !isEmpty(o);
        }

        public static <T> T emptyOfDefault(T t, T defaultValue) {
            return isEmpty(t) ? defaultValue : t;
        }

        public static <T> T nullOfDefault(T t, T defaultValue) {
            return t == null ? defaultValue : t;
        }

        /** url 编码 */
        public static String urlEncode(String text, Charset charset) {
            if(isNotEmpty(text) && isNotEmpty(charset)) {
                // 不为空 并且charset可用
                try {
                    return URLEncoder.encode(text, charset.name());
                } catch(UnsupportedEncodingException e) {
                    // do non thing
                }
            }
            return text;
        }

        /**
         * @description Map => key1=val1&key2=val2
         * @date 2019-08-20 20:42:59
         * @author houyu for.houyu@foxmail.com
         */
        public static String paramMapAsString(Map<String, Object> paramMap, Charset charset) {
            if(isNotEmpty(paramMap)) {
                StringBuilder builder = new StringBuilder(128);
                if(isEmpty(charset)) {
                    // key1=val1&key2=val2
                    paramMap.forEach((k, v) -> builder.append(k).append(Constant.EQU).append(v).append(Constant.AND_SIGN));
                } else {
                    paramMap.forEach((k, v) -> builder.append(urlEncode(k, charset)).append(Constant.EQU).append(urlEncode(String.valueOf(v), charset))
                            .append(Constant.AND_SIGN));
                }
                return builder.delete(builder.length() - 1, builder.length()).toString();
            }
            return "";
        }

        /**
         * 把浏览器的Form字符串转为Map
         */
        public static Map<String, Object> parseFormStringAsMap(String s) {
            String[] split = s.split("\n");
            Map<String, Object> targetMap = new HashMap<>(split.length);
            for (String keyAndVal : split) {
                String[] keyVal = keyAndVal.split(": ", 2);
                targetMap.put(keyVal[0], keyVal[1]);
            }
            return targetMap;
        }

    }

    /** =============================================== Util ========================================================== */
}

