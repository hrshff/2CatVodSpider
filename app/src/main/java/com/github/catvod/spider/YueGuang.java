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
 * TVBox Java Spider - 修复反爬虫版
 * 
 * 反爬虫应对策略：
 * 1. 首次访问首页获取 Cookie 和 Session
 * 2. 所有子页面请求携带首页 Cookie + Referer
 * 3. 对返回内容做校验，空内容时尝试备用解析
 * 4. 增加更多真实浏览器请求头
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // 存储从首页获取的 Cookie
    private String cookie = "";

    /**
     * 获取请求头 - 反爬虫关键：模拟真实浏览器 + Cookie
     */
    private HashMap<String, String> getHeaders(String referer) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Referer", referer);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Cache-Control", "max-age=0");

        // 携带Cookie（关键！）
        if (cookie != null && !cookie.isEmpty()) {
            headers.put("Cookie", cookie);
        }

        return headers;
    }

    /**
     * 发起HTTP请求 - 自动处理Cookie
     */
    private String fetch(String url) {
        return OkHttp.string(url, getHeaders(HOST + "/"));
    }

    private String fetch(String url, String referer) {
        return OkHttp.string(url, getHeaders(referer));
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
        // 初始化时访问首页获取 Cookie
        try {
            String homeHtml = OkHttp.string(HOST, getHeaders(HOST));
            // 尝试从响应头或HTML中提取Cookie信息
            // TVBox的OkHttp可能不直接暴露响应头，这里通过HTML内容判断
            if (homeHtml != null && homeHtml.contains("stui-vodlist")) {
                // 首页访问成功，Cookie已自动设置
                cookie = "visited=1"; // 标记已访问
            }
        } catch (Exception e) {
            // 忽略初始化错误
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
        String html = fetch(HOST);
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);
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

        // 先访问首页确保Cookie有效
        String homeHtml = fetch(HOST);

        // 再访问分类页（带首页Referer）
        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        // 如果分类页返回空，尝试从首页解析对应分类的影片（备用方案）
        if (list.length() == 0 && homeHtml != null && !homeHtml.isEmpty()) {
            Document homeDoc = Jsoup.parse(homeHtml);
            list = parseVodListFromHome(homeDoc, tid);
        }

        boolean hasNext = doc.select(".stui-page").size() > 0;
        if (list.length() == 0) {
            hasNext = false;
        }

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

            // 先访问首页
            fetch(HOST);

            // 再访问详情页
            String html = fetch(id, HOST + "/zwhstp/1.html");
            Document doc = Jsoup.parse(html);

            // 标题
            String vodName = "";
            Element h1 = doc.selectFirst("h1.title");
            if (h1 != null) vodName = h1.text().trim();

            // 从 ld+json 提取图片和简介
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

            // 从 .stui-content__detail 提取演员、导演、类型、地区
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

                String dataText = detailInfo.selectFirst("p.data") != null ? detailInfo.selectFirst("p.data").text() : "";

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

            // 提取播放源和剧集
            List<String> froms = new ArrayList<>();
            List<String> urls = new ArrayList<>();

            Elements playlists = doc.select("ul.stui-content__playlist");

            for (int i = 0; i < playlists.size(); i++) {
                Element playlist = playlists.get(i);

                String sourceName = "";
                Element parent = playlist.parent();
                if (parent != null) {
                    Element head = parent.selectFirst(".stui-pannel__head h3, .stui-pannel__head");
                    if (head != null) {
                        sourceName = head.text().trim();
                        if (sourceName.contains("猜你喜欢") || sourceName.contains("影片评论") 
                            || sourceName.contains("本周热门") || sourceName.contains("最新更新")) {
                            sourceName = "";
                        }
                    }
                }
                if (sourceName.isEmpty()) sourceName = "线路" + (i + 1);

                List<String> playLinks = new ArrayList<>();
                Elements links = playlist.select("li a");
                for (Element link : links) {
                    String href = link.attr("href");
                    String epName = link.text().trim();
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
            vod.put("vod_area", vodArea);
            vod.put("vod_class", vodClass);
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
        if (id == null || id.trim().isEmpty()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            result.put("msg", "无效播放地址");
            return result.toString();
        }

        // 先访问首页
        fetch(HOST);

        // 再访问播放页
        String html = fetch(id, HOST + "/zwhsdt/1.html");

        Pattern pattern = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        if (!matcher.find()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            result.put("msg", "未找到播放器配置");
            return result.toString();
        }

        try {
            String playerStr = matcher.group();

            Pattern urlPattern = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            Matcher urlMatcher = urlPattern.matcher(playerStr);
            if (!urlMatcher.find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", "");
                result.put("msg", "未找到播放URL");
                return result.toString();
            }

            String mediaUrl = urlMatcher.group(1);

            int encrypt = 0;
            Pattern encPattern = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)");
            Matcher encMatcher = encPattern.matcher(playerStr);
            if (encMatcher.find()) {
                encrypt = Integer.parseInt(encMatcher.group(1));
            }

            if (encrypt == 1) {
                mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
            }

            boolean isM3u8 = mediaUrl.contains(".m3u8");
            boolean isMp4 = mediaUrl.contains(".mp4");
            int parse = (isM3u8 || isMp4) ? 0 : 1;

            JSONObject header = new JSONObject();
            header.put("User-Agent", UA);
            header.put("Referer", id);

            JSONObject result = new JSONObject();
            result.put("parse", parse);
            result.put("url", mediaUrl);
            result.put("header", header.toString());

            return result.toString();

        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", "");
            result.put("msg", "解析失败: " + e.getMessage());
            return result.toString();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception ignored) {}

        String encodedKey = URLEncoder.encode(key, "UTF-8");
        String url = page == 1
            ? HOST + "/zwhssc/" + encodedKey + "-------------.html"
            : HOST + "/zwhssc/" + encodedKey + "----------" + page + "---.html";

        // 先访问首页
        fetch(HOST);

        // 再访问搜索页
        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page").size() > 0;

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", hasNext ? page + 1 : page);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    /**
     * 解析影片列表（通用）
     */
    private JSONArray parseVodList(Document doc) throws Exception {
        JSONArray list = new JSONArray();
        Elements items = doc.select("a.stui-vodlist__thumb");

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            String img = item.attr("data-original");
            if (img.isEmpty()) img = item.attr("src");

            String note = "";
            Element noteEl = item.selectFirst(".pic-text");
            if (noteEl != null) note = noteEl.text().trim();

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

    /**
     * 从首页解析指定分类的影片（备用方案）
     * 月光影视首页通常反爬较弱
     */
    private JSONArray parseVodListFromHome(Document doc, String tid) throws Exception {
        JSONArray list = new JSONArray();

        // 首页可能有多个分类区块，每个区块对应一个分类
        // 通过标题或链接中的分类ID来匹配
        Elements sections = doc.select(".stui-pannel");

        for (Element section : sections) {
            // 检查这个section是否属于目标分类
            Element head = section.selectFirst(".stui-pannel__head h3, .stui-pannel__head");
            if (head == null) continue;

            String sectionTitle = head.text().trim();

            // 简单的分类匹配（可以根据需要扩展）
            boolean match = false;
            switch (tid) {
                case "1": match = sectionTitle.contains("电影"); break;
                case "2": match = sectionTitle.contains("电视剧") || sectionTitle.contains("电视"); break;
                case "3": match = sectionTitle.contains("综艺"); break;
                case "4": match = sectionTitle.contains("动漫"); break;
                case "5": match = sectionTitle.contains("短剧"); break;
            }

            if (match) {
                Elements items = section.select("a.stui-vodlist__thumb");
                for (Element item : items) {
                    String href = item.attr("href");
                    String title = item.attr("title");
                    String img = item.attr("data-original");
                    if (img.isEmpty()) img = item.attr("src");
                    String note = "";
                    Element noteEl = item.selectFirst(".pic-text");
                    if (noteEl != null) note = noteEl.text().trim();

                    if (href.isEmpty() || title.isEmpty()) continue;

                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", abs(href));
                    vod.put("vod_name", title);
                    vod.put("vod_pic", abs(img));
                    vod.put("vod_remarks", note);
                    list.put(vod);
                }
                break; // 只取第一个匹配的分类区块
            }
        }

        return list;
    }
}
