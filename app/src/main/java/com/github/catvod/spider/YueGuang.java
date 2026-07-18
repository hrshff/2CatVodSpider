package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 月光影视 (www.shipian8.com)
 * TVBox Java Spider - 扩展写法版
 * 
 * 扩展点1: 重写 Spider.client() 返回自定义 OkHttpClient（带CookieJar）
 * 扩展点2: 重写 Spider.safeDns() 返回自定义 DNS
 * 扩展点3: 使用 proxy() 本地代理中转请求
 * 扩展点4: 使用 action() 自定义动作
 * 扩展点5: 使用 OkHttp.newCall() 获取原始 Response（含Cookie）
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";

    // 跨请求保持Cookie的存储
    private final List<Cookie> cookieStore = new ArrayList<>();
    private OkHttpClient customClient;

    /**
     * 扩展点1: 重写 client()，返回带 CookieJar 的 OkHttpClient
     * TVBox 的 OkHttp.string() 内部会优先使用 Spider.client()
     */
    @Override
    public OkHttpClient client() {
        if (customClient == null) {
            customClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.addAll(cookies);
                    }
                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        return cookieStore;
                    }
                })
                .build();
        }
        return customClient;
    }

    /**
     * 扩展点2: 重写 safeDns()，返回自定义 DNS（如 DoH）
     */
    @Override
    public Dns safeDns() {
        return Dns.SYSTEM; // 可替换为自定义DoH
    }

    private Map<String, String> getHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        return headers;
    }

    private Map<String, String> getHeader(String referer) {
        Map<String, String> headers = getHeader();
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private String abs(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return HOST + url;
    }

    /**
     * 扩展写法: 使用 OkHttp.newCall() 获取原始 Response
     * 可以拿到 Set-Cookie、状态码、完整响应头
     */
    private String fetchWithRaw(String url, String referer) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", referer != null ? referer : HOST + "/")
            .build();

        try (Response response = client().newCall(request).execute()) {
            // 打印状态码和响应头，用于调试
            int code = response.code();
            String contentType = response.header("Content-Type", "unknown");
            String body = response.body() != null ? response.body().string() : "";

            // 如果返回内容异常短，可能是拦截页
            if (body.length() < 1000) {
                System.out.println("[YueGuang] WARNING: " + url + " returned " + code + 
                    " len=" + body.length() + " type=" + contentType);
            }
            return body;
        }
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        // 预热：先访问首页，让CookieJar保存Cookie
        try {
            String homeHtml = fetchWithRaw(HOST, null);
            System.out.println("[YueGuang] init home len=" + homeHtml.length());
        } catch (Exception e) {
            System.out.println("[YueGuang] init error: " + e.getMessage());
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        classes.put(new JSONObject().put("type_id", "1").put("type_name", "电影"));
        classes.put(new JSONObject().put("type_id", "2").put("type_name", "电视剧"));
        classes.put(new JSONObject().put("type_id", "3").put("type_name", "综艺"));
        classes.put(new JSONObject().put("type_id", "4").put("type_name", "动漫"));
        classes.put(new JSONObject().put("type_id", "5").put("type_name", "短剧"));

        result.put("class", classes);
        result.put("filters", new JSONObject());
        result.put("list", new JSONArray());

        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetchWithRaw(HOST, null);
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String url = page == 1
            ? HOST + "/zwhstp/" + tid + ".html"
            : HOST + "/zwhstp/" + tid + "-" + page + ".html";

        String html = fetchWithRaw(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", hasNext ? page + 1 : page);
        result.put("limit", 24);
        result.put("total", list.length() > 0 ? page * 24 + 1 : 0);
        result.put("list", list);

        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        JSONArray list = new JSONArray();

        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;

            String html = fetchWithRaw(id, HOST + "/zwhstp/1.html");
            Document doc = Jsoup.parse(html);

            String vodName = "";
            Element h1 = doc.selectFirst("h1.title");
            if (h1 != null) vodName = h1.text().trim();

            String vodPic = "";
            String vodContent = "";
            String vodYear = "";
            Element ldScript = doc.selectFirst("script[type=application/ld+json]");
            if (ldScript != null) {
                String ldJson = ldScript.html();
                Matcher m1 = Pattern.compile("\\\"thumbnailUrl\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m1.find()) vodPic = m1.group(1);
                Matcher m2 = Pattern.compile("\\\"description\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m2.find()) vodContent = m2.group(1).replace("&amp;nbsp;", " ").trim();
                Matcher m3 = Pattern.compile("\\\"uploadDate\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(ldJson);
                if (m3.find()) {
                    String date = m3.group(1);
                    if (date.length() >= 4) vodYear = date.substring(0, 4);
                }
            }

            String vodActor = "";
            String vodDirector = "";
            String vodClass = "";
            String vodArea = "";

            Element detailInfo = doc.selectFirst(".stui-content__detail");
            if (detailInfo != null) {
                Elements actorLinks = detailInfo.select("p.data a[href*=/zwhssc/]");
                List<String> actors = new ArrayList<>();
                for (Element a : actorLinks) {
                    String name = a.text().trim();
                    if (!name.isEmpty()) actors.add(name);
                }
                vodActor = String.join(",", actors);

                Element dataP = detailInfo.selectFirst("p.data");
                String dataText = dataP != null ? dataP.text() : "";

                Matcher md = Pattern.compile("导演[:：]\\s*([^\\n]+)").matcher(dataText);
                if (md.find()) vodDirector = md.group(1).trim();
                Matcher mc = Pattern.compile("类型[:：]\\s*([^\\n]+)").matcher(dataText);
                if (mc.find()) vodClass = mc.group(1).trim();
                Matcher ma = Pattern.compile("地区[:：]\\s*([^\\n]+)").matcher(dataText);
                if (ma.find()) vodArea = ma.group(1).trim();
            }

            List<String> froms = new ArrayList<>();
            List<String> urls = new ArrayList<>();

            Elements tabs = doc.select(".stui-content__playlist");
            Elements tabNames = doc.select(".stui-pannel__head h3.title");

            for (int i = 0; i < tabs.size(); i++) {
                String sourceName = (i < tabNames.size()) ? tabNames.get(i).text().trim() : ("源" + (i + 1));
                List<String> playLinks = new ArrayList<>();
                Elements links = tabs.get(i).select("li a[href]");
                for (Element a : links) {
                    String href = a.attr("href");
                    String epName = a.text().trim();
                    if (!href.isEmpty() && !epName.isEmpty()) {
                        playLinks.add(epName + "$" + abs(href));
                    }
                }
                if (!playLinks.isEmpty()) {
                    froms.add(sourceName);
                    urls.add(String.join("#", playLinks));
                }
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);
            vod.put("vod_name", vodName);
            vod.put("vod_pic", vodPic);
            vod.put("vod_content", vodContent);
            vod.put("vod_actor", vodActor);
            vod.put("vod_director", vodDirector);
            vod.put("vod_class", vodClass);
            vod.put("vod_area", vodArea);
            vod.put("vod_year", vodYear);
            vod.put("vod_play_from", String.join("$$$", froms));
            vod.put("vod_play_url", String.join("$$$", urls));
            list.put(vod);
        }

        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id == null || id.isEmpty()) {
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", "");
            return r.toString();
        }

        String html = fetchWithRaw(id, HOST + "/zwhsdt/1.html");

        Matcher mp = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL).matcher(html);
        if (!mp.find()) {
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("url", "");
            return r.toString();
        }

        String playerStr = mp.group();
        Matcher mu = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(playerStr);
        String mediaUrl = mu.find() ? mu.group(1) : "";
        Matcher me = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)").matcher(playerStr);
        int encrypt = me.find() ? Integer.parseInt(me.group(1)) : 0;

        if (encrypt == 1 && !mediaUrl.isEmpty()) {
            mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
        }

        boolean isM3u8 = mediaUrl.contains(".m3u8");
        boolean isMp4 = mediaUrl.contains(".mp4");
        int parse = (isM3u8 || isMp4) ? 0 : 1;

        JSONObject result = new JSONObject();
        result.put("parse", parse);
        result.put("url", mediaUrl);

        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", id);
        result.put("header", new JSONObject(header).toString());

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = HOST + "/zwhssc/" + encodedKey + "-------------.html";

        String html = fetchWithRaw(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", 1);
        result.put("pagecount", hasNext ? 2 : 1);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    /**
     * 扩展点3: proxy() 本地代理
     * 当播放器无法直接访问视频URL时，通过本地代理中转
     */
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String url = params.get("url");
        if (url == null || url.isEmpty()) {
            return new Object[]{404, "text/plain", new byte[0]};
        }

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", HOST)
            .build();

        try (Response response = client().newCall(request).execute()) {
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            String contentType = response.header("Content-Type", "application/octet-stream");
            return new Object[]{response.code(), contentType, body};
        }
    }

    /**
     * 扩展点4: action() 自定义动作
     * TVBox可以通过 action 调用Spider的自定义方法
     */
    @Override
    public String action(String action) throws Exception {
        if ("clearCookie".equals(action)) {
            cookieStore.clear();
            return "{" + "\"code\":200,\"msg\":\"Cookie已清除\"" + "}";
        }
        if ("getCookieCount".equals(action)) {
            return "{" + "\"code\":200,\"count\":" + cookieStore.size() + "}";
        }
        return null;
    }

    // ========== 解析辅助方法 ==========

    private JSONArray parseVodList(Document doc) throws Exception {
        JSONArray list = new JSONArray();
        Elements items = doc.select(".stui-vodlist__thumb");

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = item.attr("data-original");
            if (img.isEmpty()) img = item.attr("data-src");
            Element noteEl = item.selectFirst(".pic-text");
            String note = noteEl != null ? noteEl.text().trim() : "";

            if (href.isEmpty() || title.isEmpty()) continue;

            JSONObject vod = new JSONObject();
            vod.put("vod_id", abs(href));
            vod.put("vod_name", title);
            vod.put("vod_pic", abs(img));
            vod.put("vod_remarks", note);
            list.put(vod);
        }
        return list;
    }
}
