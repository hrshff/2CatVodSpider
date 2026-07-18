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
 * TVBox Java Spider
 * 
 * 反爬虫应对：
 * 1. 设置真实浏览器 User-Agent
 * 2. 子页面请求携带 Referer（从首页/分类页跳转）
 * 3. 设置完整的 Accept 请求头
 * 4. TVBox 客户端 OkHttp 自动处理 Cookie 和重定向
 */
public class YueGuang extends Spider {

    private static final String HOST = "https://www.shipian8.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * 获取请求头 - 反爬虫关键：模拟真实浏览器访问
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
        return headers;
    }

    /**
     * 发起HTTP请求 - 反爬虫：始终携带Referer
     */
    private String fetch(String url) {
        return OkHttp.string(url, getHeaders(HOST + "/"));
    }

    /**
     * 发起HTTP请求 - 带指定Referer
     */
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

        // 反爬虫：分类页请求带首页Referer
        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        // 判断是否有下一页：找分页链接
        boolean hasNext = doc.select(".stui-page").size() > 0 && !doc.select(".stui-page a:contains(下一页)").isEmpty();
        if (!hasNext) {
            // 备用判断：如果列表满24条且有分页区域
            hasNext = list.length() >= 24 && doc.select(".stui-page").size() > 0;
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

            // 反爬虫：详情页请求带分类页Referer
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
                // 主演
                Elements actorLinks = detailInfo.select("p.data a[href*=/zwhssc/]");
                List<String> actors = new ArrayList<>();
                for (Element a : actorLinks) {
                    String actorName = a.text().trim();
                    if (!actorName.isEmpty()) actors.add(actorName);
                }
                vodActor = String.join(",", actors);

                // 从文本中提取导演、类型、地区
                String dataText = detailInfo.selectFirst("p.data") != null ? detailInfo.selectFirst("p.data").text() : "";

                // 尝试找导演
                Pattern dirPattern = Pattern.compile("导演[:：]\\s*([^\\n]+)");
                Matcher dirMatcher = dirPattern.matcher(dataText);
                if (dirMatcher.find()) vodDirector = dirMatcher.group(1).trim();

                // 尝试找类型
                Pattern classPattern = Pattern.compile("类型[:：]\\s*([^\\n]+)");
                Matcher classMatcher = classPattern.matcher(dataText);
                if (classMatcher.find()) vodClass = classMatcher.group(1).trim();

                // 尝试找地区
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

                // 尝试找源名称：从上一个 .stui-pannel__head 或 h3 获取
                String sourceName = "";
                Element parent = playlist.parent();
                if (parent != null) {
                    Element head = parent.selectFirst(".stui-pannel__head h3, .stui-pannel__head");
                    if (head != null) {
                        sourceName = head.text().trim();
                        // 过滤掉非播放源标题
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

        // 反爬虫：播放页请求带详情页Referer
        String html = fetch(id, HOST + "/zwhsdt/1.html");

        // 提取 player_aaaa 变量
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

            // 提取 url
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

            // 月光影视 encrypt=0，url是明文直链，无需解码
            // 但为了兼容，仍然检查 encrypt 字段
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

        // 反爬虫：搜索页请求带首页Referer
        String html = fetch(url, HOST + "/");
        Document doc = Jsoup.parse(html);
        JSONArray list = parseVodList(doc);

        boolean hasNext = doc.select(".stui-page").size() > 0 && !doc.select(".stui-page a:contains(下一页)").isEmpty();
        if (!hasNext) {
            hasNext = list.length() >= 24 && doc.select(".stui-page").size() > 0;
        }

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", hasNext ? page + 1 : page);
        result.put("limit", 24);
        result.put("total", list.length());
        result.put("list", list);

        return result.toString();
    }

    /**
     * 解析影片列表（首页/分类页/搜索页通用）
     * 月光影视使用 .stui-vodlist__thumb
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
}
