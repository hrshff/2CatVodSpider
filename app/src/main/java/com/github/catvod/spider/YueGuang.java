package com.github.catvod.spider;

import android.content.Context;
import android.util.Log;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 月光影视 (www.shipian8.com)
 * 修复：统一为 Result 工具类风格，保留 CookieJar 反爬，修正元数据计算
 */
public class YueGuang extends Spider {

    private static final String SITE_URL = "https://www.shipian8.com";

    private static final List<Cookie> cookieStore = new ArrayList<>();
    private static OkHttpClient customClient;

    public static OkHttpClient client() {
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

    private HashMap<String, String> getHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Util.CHROME);
        headers.put("Referer", TextUtils.isEmpty(referer) ? SITE_URL + "/" : referer);
        return headers;
    }

    private String fetch(String url, String referer) throws Exception {
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", TextUtils.isEmpty(referer) ? SITE_URL + "/" : referer)
            .build();
        try (Response response = client().newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return SITE_URL + url;
    }

    private boolean isValidHtml(String html) {
        if (html == null || html.length() < 5000) return false;
        return html.contains("stui-vodlist") || html.contains("vodlist") || html.contains("class=\"stui-") || html.contains("player_");
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("1", "电影"));
        classes.add(new Class("2", "电视剧"));
        classes.add(new Class("3", "综艺"));
        classes.add(new Class("4", "动漫"));
        classes.add(new Class("5", "短剧"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetch(SITE_URL, null);
        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String url = page == 1
            ? SITE_URL + "/zwhstp/" + tid + ".html"
            : SITE_URL + "/zwhstp/" + tid + "-" + page + ".html";

        Log.d("YueGuang", "[YueGuang-DEBUG] categoryContent url=" + url);
        String html = fetch(url, SITE_URL + "/");

        if (!isValidHtml(html)) {
            Log.d("YueGuang", "[YueGuang-DEBUG] categoryContent INVALID html, fallback to home");
            String homeHtml = fetch(SITE_URL, null);
            if (isValidHtml(homeHtml)) {
                Document homeDoc = Jsoup.parse(homeHtml);
                List<Vod> list = parseVodList(homeDoc);
                return Result.get().vod(list).page(page, page, 24, list.size()).string();
            }
        }

        Document doc = Jsoup.parse(html);
        List<Vod> list = parseVodList(doc);

        // 从分页控件提取真实总页数
        boolean hasNext = doc.select(".stui-page, .page, .page-link, .pagination, .fed-page-info").size() > 0 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = hasNext ? 99999 : page * list.size();

        Element numEl = doc.selectFirst(".stui-page .num");
        if (numEl != null) {
            String text = numEl.text(); // e.g. "2/2812"
            String[] arr = text.split("/");
            if (arr.length == 2) {
                try {
                    pageCount = Integer.parseInt(arr[1].trim());
                    total = pageCount * Math.max(list.size(), 1);
                } catch (Exception ignored) {}
            }
        } else {
            // 从尾页链接提取（备用）
            Elements pageLinks = doc.select(".stui-page a");
            for (Element a : pageLinks) {
                String text = a.text();
                if (text.contains("尾页") || text.contains("最后一页")) {
                    String href = a.attr("href");
                    Matcher m = Pattern.compile("-(\\d+)\\.html").matcher(href);
                    if (m.find()) {
                        try {
                            pageCount = Integer.parseInt(m.group(1));
                            total = pageCount * Math.max(list.size(), 1);
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        // 如果无法提取，保持原有逻辑
        if (pageCount <= page) {
            pageCount = hasNext ? page + 1 : page;
        }
        if (total <= 0) {
            total = hasNext ? 99999 : page * list.size();
        }

        // limit 使用实际列表大小（月光每页固定20项）
        int limit = list.size() > 0 ? list.size() : 24;

        Log.d("YueGuang", "[YueGuang-DEBUG] categoryContent page=" + page + " items=" + list.size() + " pageCount=" + pageCount + " total=" + total + " limit=" + limit);
        return Result.get().vod(list).page(page, pageCount, limit, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        if (TextUtils.isEmpty(id)) return "";

        String html = fetch(id, SITE_URL + "/zwhstp/1.html");
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1.title");
        if (h1 != null) vod.setVodName(h1.text().trim());

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
        vod.setVodPic(vodPic);
        vod.setVodContent(vodContent);
        vod.setVodYear(vodYear);

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
                if (!TextUtils.isEmpty(name)) actors.add(name);
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

        vod.setVodActor(vodActor);
        vod.setVodDirector(vodDirector);
        vod.setTypeName(vodClass);
        vod.setVodArea(vodArea);

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
                if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(epName)) {
                    playLinks.add(epName + "$" + fixUrl(href));
                }
            }
            if (!playLinks.isEmpty()) {
                froms.add(sourceName);
                urls.add(String.join("#", playLinks));
            }
        }

        vod.setVodPlayFrom(String.join("$$$", froms));
        vod.setVodPlayUrl(String.join("$$$", urls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").string();
        }

        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent start, id=" + id + ", flag=" + flag);

        String html = fetch(id, SITE_URL + "/zwhsdt/1.html");
        String preview = html.length() > 200 ? html.substring(0, 200) : html;
        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent html len=" + html.length() + " preview=" + preview.replace("\n", " "));

        if (!isValidHtml(html)) {
            Log.d("YueGuang", "[YueGuang-DEBUG] playerContent INVALID html");
            return Result.get().url("").string();
        }

        Matcher mp = Pattern.compile("var\\s+player_\\w+\\s*=\\s*(\\{.*?\\})\\s*</script>", Pattern.DOTALL).matcher(html);
        boolean found = mp.find();
        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent primary regex found=" + found);

        if (!found) {
            mp = Pattern.compile("var\\s+player_\\w+\\s*=\\s*(\\{.*?\\})(?:;|\\s*<|\\s*$)", Pattern.DOTALL).matcher(html);
            found = mp.find();
            Log.d("YueGuang", "[YueGuang-DEBUG] playerContent fallback regex found=" + found);
        }

        if (!found) {
            Log.d("YueGuang", "[YueGuang-DEBUG] playerContent NO MATCH");
            return Result.get().url("").string();
        }

        String playerStr = mp.group(1);
        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent matched=" + playerStr.substring(0, Math.min(200, playerStr.length())));

        Matcher mu = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(playerStr);
        String mediaUrl = mu.find() ? mu.group(1) : "";
        Matcher me = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)").matcher(playerStr);
        int encrypt = me.find() ? Integer.parseInt(me.group(1)) : 0;

        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent raw url=" + mediaUrl + ", encrypt=" + encrypt);

        if (encrypt == 1 && !TextUtils.isEmpty(mediaUrl)) {
            mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
        }
        mediaUrl = mediaUrl.replace("\\/", "/");

        Log.d("YueGuang", "[YueGuang-DEBUG] playerContent final url=" + mediaUrl);

        boolean isM3u8 = mediaUrl.contains(".m3u8");
        boolean isMp4 = mediaUrl.contains(".mp4");
        int parse = (isM3u8 || isMp4) ? 0 : 1;

        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", id);

        return Result.get().url(mediaUrl).parse(parse).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = SITE_URL + "/zwhssc/" + encodedKey + "-------------.html";

        String html = fetch(url, SITE_URL + "/");
        Document doc = Jsoup.parse(html);
        List<Vod> vodList = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page, .page, .page-link, .pagination, .fed-page-info").size() > 0 || vodList.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = vodList.size();
        int limit = vodList.size() > 0 ? vodList.size() : 24;

        return Result.get().vod(vodList).page(page, pageCount, limit, total).string();
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        String url = params.get("url");
        if (TextUtils.isEmpty(url)) {
            return new Object[]{404, "text/plain", new byte[0]};
        }

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", Util.CHROME)
            .header("Referer", SITE_URL)
            .build();

        try (Response response = client().newCall(request).execute()) {
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            String contentType = response.header("Content-Type", "application/octet-stream");
            return new Object[]{response.code(), contentType, body};
        }
    }

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

    private List<Vod> parseVodList(Document doc) throws Exception {
        List<Vod> list = new ArrayList<>();
        Elements items = doc.select(".stui-vodlist__thumb");

        if (items.isEmpty()) {
            items = doc.select(".fed-list-pics, .myui-vodlist__thumb, .module-poster-item");
        }
        if (items.isEmpty()) {
            items = doc.select("a[href*=/zwhsdt/]");
        }

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = item.attr("data-original");
            if (TextUtils.isEmpty(img)) img = item.attr("data-src");
            if (TextUtils.isEmpty(img)) {
                Element imgEl = item.selectFirst("img");
                if (imgEl != null) {
                    img = imgEl.attr("data-original");
                    if (TextUtils.isEmpty(img)) img = imgEl.attr("src");
                }
            }
            // 第二页图片在 style="background-image: url(...)" 中
            if (TextUtils.isEmpty(img)) {
                String style = item.attr("style");
                if (!TextUtils.isEmpty(style)) {
                    Matcher m = Pattern.compile("background-image\\s*:\\s*url\\(([^)]+)\\)").matcher(style);
                    if (m.find()) {
                        img = m.group(1).trim();
                        if ((img.startsWith("\"") && img.endsWith("\"")) ||
                            (img.startsWith("'") && img.endsWith("'"))) {
                            img = img.substring(1, img.length() - 1);
                        }
                    }
                }
            }
            Element noteEl = item.selectFirst(".pic-text, .fed-list-remarks, .module-item-note");
            String note = noteEl != null ? noteEl.text().trim() : "";

            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

            Vod vod = new Vod();
            vod.setVodId(fixUrl(href));
            vod.setVodName(title);
            vod.setVodPic(fixUrl(img));
            vod.setVodRemarks(note);
            list.add(vod);
        }
        return list;
    }
}
