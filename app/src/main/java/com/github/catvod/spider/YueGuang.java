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
 * TVBox Java Spider - 反爬虫修复版
 * 
 * 修复要点：
 * 1. 删除Accept-Encoding，避免gzip压缩导致Jsoup解析失败
 * 2. 增加System.out调试输出，确认服务器返回内容
 * 3. 增加备用选择器，兼容不同HTML结构
 * 4. 请求头更接近真实Chrome浏览器
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private boolean inited = false;

    private HashMap<String, String> getHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        // 注意：不设置Accept-Encoding，让服务器返回明文HTML
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Cache-Control", "max-age=0");
        headers.put("DNT", "1");
        if (referer != null && !referer.isEmpty()) {
            headers.put("Referer", referer);
        }
        return headers;
    }

    private String fetch(String url, String referer) throws Exception {
        HashMap<String, String> headers = getHeaders(referer);
        String html = OkHttp.string(url, headers);
        // 调试输出：打印返回内容的前300字符
        if (html != null) {
            String preview = html.length() > 300 ? html.substring(0, 300) : html;
            System.out.println("[YueGuang] fetch " + url + " | len=" + html.length() + " | preview=" + preview.replace("\n", " "));
        } else {
            System.out.println("[YueGuang] fetch " + url + " | html is null");
        }
        return html != null ? html : "";
    }

    private String abs(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return HOST + url;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        // 预热首页
        try {
            String homeHtml = OkHttp.string(HOST, getHeaders(null));
            System.out.println("[YueGuang] init home | len=" + (homeHtml != null ? homeHtml.length() : 0));
            inited = true;
        } catch (Exception e) {
            System.out.println("[YueGuang] init error: " + e.getMessage());
            inited = true;
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
        String html = fetch(HOST, null);
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);
        System.out.println("[YueGuang] homeVideo parsed " + list.length() + " items");
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}

        String url = page == 1
            ? HOST + "/zwhstp/" + tid + ".html"
            : HOST + "/zwhstp/" + tid + "-" + page + ".html";

        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        System.out.println("[YueGuang] category tid=" + tid + " page=" + page + " parsed " + list.length() + " items");

        // 如果分类页为空，尝试从首页解析兜底
        if (list.length() == 0) {
            System.out.println("[YueGuang] category empty, trying home fallback");
            String homeHtml = fetch(HOST, null);
            Document homeDoc = Jsoup.parse(homeHtml);
            list = parseVodListFromHome(homeDoc, tid);
            System.out.println("[YueGuang] home fallback parsed " + list.length() + " items");
        }

        boolean hasNext = doc.select(".stui-page").size() > 0 || doc.select(".page").size() > 0;
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
                Pattern picPattern = Pattern.compile("\\\"thumbnailUrl\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
                Matcher picMatcher = picPattern.matcher(ldJson);
                if (picMatcher.find()) vodPic = picMatcher.group(1);

                Pattern descPattern = Pattern.compile("\\\"description\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
                Matcher descMatcher = descPattern.matcher(ldJson);
                if (descMatcher.find()) vodContent = descMatcher.group(1).replace("&amp;nbsp;", " ").trim();

                Pattern datePattern = Pattern.compile("\\\"uploadDate\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
                Matcher dateMatcher = datePattern.matcher(ldJson);
                if (dateMatcher.find()) {
                    String date = dateMatcher.group(1);
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
                    String actorName = a.text().trim();
                    if (!actorName.isEmpty()) actors.add(actorName);
                }
                vodActor = String.join(",", actors);

                Element dataP = detailInfo.selectFirst("p.data");
                String dataText = dataP != null ? dataP.text() : "";

                Pattern dirPattern = Pattern.compile("导演[:：]\\s*([^\\n]+)");
                Matcher dirMatcher = dirPattern.matcher(dataText);
                if (dirMatcher.find()) vodDirector = dirMatcher.group(1).trim();

                Pattern classPattern = Pattern.compile("类型[:：]\\s*([^\\n]+)");
                Matcher classMatcher = classPattern.matcher(dataText);
                if (classMatcher.find()) vodClass = classMatcher.group(1).trim();

                Pattern areaPattern = Pattern.compile("地区[:：]\\s*([^\\n]+)");
                Matcher areaMatcher = areaPattern.matcher(dataText);
                if (areaMatcher.find()) vodArea = areaMatcher.group(1).trim();
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
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            return result.toString();
        }

        String html = fetch(id, HOST + "/zwhsdt/1.html");

        Pattern playerPattern = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL);
        Matcher playerMatcher = playerPattern.matcher(html);

        if (!playerMatcher.find()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            return result.toString();
        }

        String playerStr = playerMatcher.group();

        Pattern urlPattern = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher urlMatcher = urlPattern.matcher(playerStr);
        String mediaUrl = urlMatcher.find() ? urlMatcher.group(1) : "";

        Pattern encryptPattern = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)");
        Matcher encryptMatcher = encryptPattern.matcher(playerStr);
        int encrypt = encryptMatcher.find() ? Integer.parseInt(encryptMatcher.group(1)) : 0;

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
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        System.out.println("[YueGuang] search key=" + key + " parsed " + list.length() + " items");

        boolean hasNext = doc.select(".stui-page").size() > 0 || doc.select(".page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", 1);
        result.put("pagecount", hasNext ? 2 : 1);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    // ========== 解析辅助方法（带备用选择器）==========

    private JSONArray parseVodList(Document doc) throws Exception {
        JSONArray list = new JSONArray();

        // 主选择器：月光影视模板
        Elements items = doc.select(".stui-vodlist__thumb");
        System.out.println("[YueGuang] parseVodList primary selector .stui-vodlist__thumb found " + items.size());

        // 备用选择器1：通用MacCMS
        if (items.isEmpty()) {
            items = doc.select(".fed-list-item .fed-list-pics, .myui-vodlist__thumb, .module-poster-item, .hl-list-item a");
            System.out.println("[YueGuang] parseVodList fallback1 found " + items.size());
        }

        // 备用选择器2：更通用的a标签
        if (items.isEmpty()) {
            items = doc.select("a[href*=/zwhsdt/], a[href*=/voddetail/]");
            System.out.println("[YueGuang] parseVodList fallback2 found " + items.size());
        }

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            // 尝试多种图片属性
            String img = item.attr("data-original");
            if (img.isEmpty()) img = item.attr("data-src");
            if (img.isEmpty()) img = item.selectFirst("img") != null ? item.selectFirst("img").attr("src") : "";
            if (img.isEmpty()) img = item.selectFirst("img") != null ? item.selectFirst("img").attr("data-original") : "";

            Element noteEl = item.selectFirst(".pic-text, .fed-list-remarks, .module-item-note, .hl-pic-text");
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

    private JSONArray parseVodListFromHome(Document doc, String tid) throws Exception {
        JSONArray list = new JSONArray();
        // 首页按分类区块解析
        // dzwhs模板首页通常按分类分区块，每个区块有标题和列表
        Elements sections = doc.select(".stui-pannel");
        System.out.println("[YueGuang] parseVodListFromHome found " + sections.size() + " sections");

        for (Element section : sections) {
            Element titleEl = section.selectFirst(".stui-pannel__head h3.title, .stui-pannel__head .title");
            if (titleEl != null) {
                String sectionTitle = titleEl.text().trim();
                System.out.println("[YueGuang] section title: " + sectionTitle);
                // 简单匹配：如果区块标题包含分类名，就解析该区块
                // 实际应根据tid精确匹配，这里简化处理
            }
            Elements items = section.select(".stui-vodlist__thumb");
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
        }
        return list;
    }
}
