package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 月光影视 (www.shipian8.com)
 * TVBox Java Spider - 诊断版
 * 
 * 把服务器返回内容直接显示在TVBox中，帮助诊断反爬虫问题
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private HashMap<String, String> getHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private String fetch(String url, String referer) throws Exception {
        return OkHttp.string(url, getHeaders(referer));
    }

    private String abs(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return HOST + url;
    }

    // 创建诊断条目，显示服务器返回的内容
    private JSONObject makeDebugItem(String title, String preview) throws Exception {
        JSONObject vod = new JSONObject();
        vod.put("vod_id", HOST);
        vod.put("vod_name", title);
        vod.put("vod_pic", "");
        vod.put("vod_remarks", preview.length() > 50 ? preview.substring(0, 50) : preview);
        vod.put("vod_content", preview);
        return vod;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
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
        String html = fetch(HOST, null);
        String preview = html != null && html.length() > 200 ? html.substring(0, 200) : (html != null ? html : "null");

        Document doc = Jsoup.parse(html != null ? html : "");
        JSONArray list = parseVodList(doc);

        // 如果首页也解析不到，显示诊断信息
        if (list.length() == 0) {
            list.put(makeDebugItem("首页诊断", "首页返回长度=" + (html != null ? html.length() : 0) + " 前100字=" + preview.substring(0, Math.min(100, preview.length()))));
        }

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

        String html = fetch(url, HOST + "/");
        String preview = html != null && html.length() > 300 ? html.substring(0, 300) : (html != null ? html : "null");

        Document doc = Jsoup.parse(html != null ? html : "");
        JSONArray list = parseVodList(doc);

        // 如果分类页为空，尝试从首页提取
        if (list.length() == 0) {
            String homeHtml = fetch(HOST, null);
            Document homeDoc = Jsoup.parse(homeHtml != null ? homeHtml : "");
            list = parseVodList(homeDoc);
        }

        // 如果还是空，显示诊断信息
        if (list.length() == 0) {
            String info = "分类页返回长度=" + (html != null ? html.length() : 0) + " 前150字=" + preview.substring(0, Math.min(150, preview.length()));
            list.put(makeDebugItem("分类页诊断", info));
        }

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;
        if (list.length() == 0) hasNext = false;

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

            String html = fetch(id, HOST + "/zwhstp/1.html");
            Document doc = Jsoup.parse(html != null ? html : "");

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

        String html = fetch(id, HOST + "/zwhsdt/1.html");

        Matcher mp = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL).matcher(html != null ? html : "");
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
        header.put("User-Agent", UA);
        header.put("Referer", id);
        result.put("header", new JSONObject(header).toString());

        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = HOST + "/zwhssc/" + encodedKey + "-------------.html";

        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html != null ? html : "");
        JSONArray list = parseVodList(doc);

        if (list.length() == 0) {
            String preview = html != null && html.length() > 200 ? html.substring(0, 200) : (html != null ? html : "null");
            String info = "搜索页返回长度=" + (html != null ? html.length() : 0) + " 前100字=" + preview.substring(0, Math.min(100, preview.length()));
            list.put(makeDebugItem("搜索诊断", info));
        }

        boolean hasNext = doc.select(".stui-page, .page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", 1);
        result.put("pagecount", hasNext ? 2 : 1);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    // ========== 解析辅助方法（多选择器兼容）==========

    private JSONArray parseVodList(Document doc) throws Exception {
        JSONArray list = new JSONArray();

        // 主选择器
        Elements items = doc.select(".stui-vodlist__thumb");

        // 备用1
        if (items.isEmpty()) {
            items = doc.select(".fed-list-pics, .myui-vodlist__thumb, .module-poster-item");
        }

        // 备用2：通用a标签
        if (items.isEmpty()) {
            items = doc.select("a[href*=/zwhsdt/]");
        }

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = item.attr("data-original");
            if (img.isEmpty()) img = item.attr("data-src");
            if (img.isEmpty()) {
                Element imgEl = item.selectFirst("img");
                if (imgEl != null) {
                    img = imgEl.attr("data-original");
                    if (img.isEmpty()) img = imgEl.attr("src");
                }
            }
            Element noteEl = item.selectFirst(".pic-text, .fed-list-remarks, .module-item-note");
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
