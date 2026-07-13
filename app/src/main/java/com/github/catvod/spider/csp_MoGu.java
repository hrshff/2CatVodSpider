package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================
 * TVBox Spider 插件 — 蘑菇影视 (www.5o5k.com)
 * ============================================================
 * 基于 MacCMS V10 架构，支持首页、分类、搜索、详情、播放
 *
 * 配置说明:
 * {
 *     "key": "csp_MoGu",
 *     "name": "蘑菇影视",
 *     "type": 3,
 *     "api": "csp_MoGu",
 *     "searchable": 1,
 *     "quickSearch": 1,
 *     "filterable": 1
 * }
 * ============================================================
 */
public class csp_MoGu extends Spider {

    // ==================== 站点配置 ====================
    private static final String SITE_NAME = "蘑菇影视";
    private static final String SITE_URL  = "https://www.5o5k.com";

    /** 用户指定的 User-Agent */
    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 " +
        "Mobile Safari/537.36";

    // 分类ID映射 (根据实际站点结构调整)
    private static final List<String> TYPE_IDS   = Arrays.asList("1", "2", "3", "4", "48", "49");
    private static final List<String> TYPE_NAMES = Arrays.asList("电影", "连续剧", "综艺", "短剧", "动漫", "影视解说");

    // ==================== 生命周期 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        // 如果ext不为空，可以从中读取自定义配置
    }

    @Override
    public void destroy() {
        // 清理资源
    }

    // ==================== 首页 ====================

    /**
     * 首页内容
     * 返回分类列表 + 筛选条件 + 推荐影片
     */
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (int i = 0; i < TYPE_IDS.size(); i++) {
            classes.add(new Class(TYPE_IDS.get(i), TYPE_NAMES.get(i)));
        }

        // 获取首页推荐影片
        String html = OkHttp.string(SITE_URL, getHeader());
        List<Vod> list = parseVodListFromHtml(html, "a[href*=/voddetail/]");

        // 构建筛选条件 (可选)
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        if (filter) {
            // 可为每个分类添加筛选条件，如年份、地区等
            // 这里简化处理，如需可扩展
        }

        // 手动构建 JSON 返回
        JSONObject result = new JSONObject();
        JSONArray classArray = new JSONArray();
        for (Class cls : classes) {
            JSONObject c = new JSONObject();
            c.put("type_id", cls.getTypeId());
            c.put("type_name", cls.getTypeName());
            classArray.put(c);
        }
        result.put("class", classArray);

        JSONArray jsonList = new JSONArray();
        for (Vod vod : list) {
            jsonList.put(vodToJson(vod));
        }
        result.put("list", jsonList);

        // 如果有筛选条件，可以在这里添加 filters
        if (filter && !filters.isEmpty()) {
            JSONObject filtersObj = new JSONObject();
            for (Map.Entry<String, List<Filter>> entry : filters.entrySet()) {
                JSONArray filterArray = new JSONArray();
                for (Filter f : entry.getValue()) {
                    JSONObject fo = new JSONObject();
                    fo.put("key", f.getKey());
                    fo.put("name", f.getName());
                    JSONArray valueArray = new JSONArray();
                    for (Filter.Value v : f.getValue()) {
                        JSONObject vo = new JSONObject();
                        vo.put("n", v.getN());
                        vo.put("v", v.getV());
                        valueArray.put(vo);
                    }
                    fo.put("value", valueArray);
                    filterArray.put(fo);
                }
                filtersObj.put(entry.getKey(), filterArray);
            }
            result.put("filters", filtersObj);
        }

        return result.toString();
    }

    /**
     * 首页推荐视频 (单独调用)
     */
    @Override
    public String homeVideoContent() throws Exception {
        String html = OkHttp.string(SITE_URL, getHeader());
        List<Vod> list = parseVodListFromHtml(html, "a[href*=/voddetail/]");

        JSONObject result = new JSONObject();
        JSONArray jsonList = new JSONArray();
        for (Vod vod : list) {
            jsonList.put(vodToJson(vod));
        }
        result.put("list", jsonList);
        return result.toString();
    }

    // ==================== 分类 ====================

    /**
     * 分类内容
     * @param tid   分类ID
     * @param pg    页码
     * @param filter 是否启用筛选
     * @param extend 筛选参数
     */
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        // 构建分类URL: /vodshow/{tid}-----------{pg}.html
        String url = SITE_URL + "/vodshow/" + tid + "-----------" + pg + ".html";

        String html = OkHttp.string(url, getHeader());
        List<Vod> list = parseVodListFromHtml(html, "a[href*=/voddetail/]");

        // 解析总页数
        int pageCount = parseTotalPage(html);

        JSONObject result = new JSONObject();
        result.put("page", Integer.parseInt(pg));
        result.put("pagecount", pageCount > 0 ? pageCount : 1);
        result.put("limit", 40);
        result.put("total", list.size());

        JSONArray jsonList = new JSONArray();
        for (Vod vod : list) {
            jsonList.put(vodToJson(vod));
        }
        result.put("list", jsonList);

        return result.toString();
    }

    // ==================== 详情 ====================

    /**
     * 详情内容
     * @param ids 影片URL列表，取第一个
     */
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        // 如果传入的是相对路径，补全URL
        if (!id.startsWith("http")) {
            id = SITE_URL + id;
        }

        String html = OkHttp.string(id, getHeader());
        Document doc = Jsoup.parse(html);

        // 提取影片ID
        String vodId = extractIdFromUrl(id);

        // 基本信息
        String vodName = getMeta(doc, "itemprop", "name");
        String vodPic  = getMeta(doc, "itemprop", "image");
        if (vodPic.isEmpty()) {
            Element img = doc.selectFirst(".vod-pic img, .detail-pic img, .poster img");
            if (img != null) {
                vodPic = img.attr("src");
                if (vodPic.isEmpty()) vodPic = img.attr("data-original");
            }
        }
        String vodActor    = getMeta(doc, "itemprop", "actor");
        String vodDirector = getMeta(doc, "itemprop", "director");
        String vodClass    = getMeta(doc, "itemprop", "class");
        String vodArea     = getMeta(doc, "itemprop", "contentLocation");
        String vodContent  = getMeta(doc, "name", "description");
        if (vodContent.isEmpty()) vodContent = getMeta(doc, "itemprop", "description");

        // 解析播放源和剧集
        // 格式: vod_play_from = "线路1$$$线路2$$$线路3"
        // 格式: vod_play_url  = "第1集$url1#第2集$url2$$$第1集$url1#..."
        StringBuilder vodPlayFrom = new StringBuilder();
        StringBuilder vodPlayUrl  = new StringBuilder();

        // 提取所有播放链接 /vodplay/{vid}-{sid}-{ep}.html
        Pattern pt = Pattern.compile("/vodplay/(\d+)-(\d+)-(\d+)\.html");
        Matcher m = pt.matcher(html);

        Map<String, List<String[]>> sourceMap = new LinkedHashMap<>(); // sid -> [(epName, url)]
        Map<String, String> sourceNameMap = new HashMap<>();

        while (m.find()) {
            String vid = m.group(1);
            String sid = m.group(2);
            String ep  = m.group(3);
            String playUrl = "/vodplay/" + vid + "-" + sid + "-" + ep + ".html";
            String epName = "第" + String.format("%02d", Integer.parseInt(ep)) + "集";

            // 尝试从a标签获取真实名称
            Elements links = doc.select("a[href=" + playUrl + "]");
            for (Element link : links) {
                String txt = link.text().trim();
                if (!txt.isEmpty() && !txt.equals("立即播放")) {
                    epName = txt;
                    break;
                }
            }

            sourceMap.putIfAbsent(sid, new ArrayList<>());
            sourceMap.get(sid).add(new String[]{epName, SITE_URL + playUrl});
            if (!sourceNameMap.containsKey(sid)) {
                sourceNameMap.put(sid, "线路" + sid);
            }
        }

        // 提取源名称 (tab标签)
        Elements tabs = doc.select(".play-source-tab, .source-tab, .tab-item, .play_from, [data-from]");
        for (int i = 0; i < tabs.size(); i++) {
            Element tab = tabs.get(i);
            String txt = tab.text().trim();
            if (!txt.isEmpty()) {
                String sid = tab.attr("data-from");
                if (sid.isEmpty()) sid = String.valueOf(i + 1);
                sourceNameMap.put(sid, txt);
            }
        }

        // 构建vod_play_from和vod_play_url
        int sourceIndex = 0;
        for (Map.Entry<String, List<String[]>> entry : sourceMap.entrySet()) {
            String sid = entry.getKey();
            if (sourceIndex > 0) {
                vodPlayFrom.append("$$$");
                vodPlayUrl.append("$$$");
            }
            vodPlayFrom.append(sourceNameMap.getOrDefault(sid, "线路" + sid));

            List<String[]> episodes = entry.getValue();
            for (int i = 0; i < episodes.size(); i++) {
                String[] ep = episodes.get(i);
                if (i > 0) vodPlayUrl.append("#");
                vodPlayUrl.append(ep[0]).append("$").append(ep[1]);
            }
            sourceIndex++;
        }

        // 手动构建详情 JSON
        JSONObject result = new JSONObject();
        JSONArray listArray = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("vod_id", id);
        item.put("vod_name", vodName);
        item.put("vod_pic", vodPic);
        item.put("type_name", vodClass);
        item.put("vod_area", vodArea);
        item.put("vod_actor", vodActor);
        item.put("vod_director", vodDirector);
        item.put("vod_content", vodContent);
        item.put("vod_play_from", vodPlayFrom.toString());
        item.put("vod_play_url", vodPlayUrl.toString());
        listArray.put(item);
        result.put("list", listArray);

        return result.toString();
    }

    // ==================== 搜索 ====================

    /**
     * 搜索内容
     * @param key   搜索关键词
     * @param quick 是否快速搜索
     */
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    /**
     * 搜索内容 (带分页)
     */
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String enc = URLEncoder.encode(key, "UTF-8");
        String url = "1".equals(pg)
            ? SITE_URL + "/vodsearch/" + enc + "-------------.html"
            : SITE_URL + "/vodsearch/" + enc + "--" + pg + "---.html";

        String html = OkHttp.string(url, getHeader());
        List<Vod> list = parseVodListFromHtml(html, "a[href*=/voddetail/]");

        int pageCount = parseTotalPage(html);
        if (pageCount < 0) pageCount = 1;

        JSONObject result = new JSONObject();
        result.put("page", Integer.parseInt(pg));
        result.put("pagecount", pageCount);
        result.put("limit", 40);
        result.put("total", list.size());

        JSONArray jsonList = new JSONArray();
        for (Vod vod : list) {
            jsonList.put(vodToJson(vod));
        }
        result.put("list", jsonList);

        return result.toString();
    }

    // ==================== 播放 ====================

    /**
     * 播放内容解析
     * @param flag     播放源标识
     * @param id       播放页URL
     * @param vipFlags VIP标识列表
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // id 是完整的播放页URL，如 https://www.5o5k.com/vodplay/353775-3-1.html
        String html = OkHttp.string(id, getHeader());

        // 解析 player_aaaa 配置
        String playUrl = "";
        Matcher pm = Pattern.compile("var player_aaaa=\\{([^;]+)\\};", Pattern.DOTALL).matcher(html);
        if (!pm.find()) {
            pm = Pattern.compile("var player_[a-zA-Z0-9_]+=\\{([^;]+)\\};", Pattern.DOTALL).matcher(html);
        }
        if (pm.find()) {
            String cfg = pm.group(1);
            Matcher m = Pattern.compile("\"url\"\s*:\s*\"([^\"]+)\"").matcher(cfg);
            if (m.find()) {
                playUrl = decodeVideoUrl(m.group(1));
            }
        }

        // 如果上面没找到，尝试直接匹配m3u8
        if (playUrl.isEmpty()) {
            Matcher m3u8 = Pattern.compile("(https?://[^\s\"']+\.m3u8)").matcher(html);
            if (m3u8.find()) playUrl = m3u8.group(1);
        }

        // 手动构建播放 JSON
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", playUrl);

        // 添加 header
        JSONObject headerObj = new JSONObject();
        Map<String, String> headers = getHeader();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerObj.put(entry.getKey(), entry.getValue());
        }
        result.put("header", headerObj);

        return result.toString();
    }

    // ==================== 视频格式检测 ====================

    @Override
    public boolean isVideoFormat(String url) {
        return url.contains(".m3u8") || url.contains(".mp4") || url.contains(".flv");
    }

    @Override
    public boolean manualVideoCheck() {
        return false;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 将 Vod 对象转换为 JSONObject
     */
    private JSONObject vodToJson(Vod vod) {
        JSONObject item = new JSONObject();
        item.put("vod_id", vod.getVodId());
        item.put("vod_name", vod.getVodName());
        item.put("vod_pic", vod.getVodPic());
        item.put("vod_remarks", vod.getVodRemarks());
        return item;
    }

    /**
     * 构建请求头
     */
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", USER_AGENT);
        header.put("Referer", SITE_URL + "/");
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return header;
    }

    /**
     * 从HTML解析影片列表
     */
    private List<Vod> parseVodListFromHtml(String html, String selector) {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(selector);

        for (Element item : items) {
            String href = item.attr("href");
            String title = item.attr("title");
            if (title.isEmpty()) title = item.text().trim();

            String vid = extractIdFromUrl(href);
            if (vid.isEmpty()) continue;

            // 海报
            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (pic.isEmpty()) pic = img.attr("src");
            } else {
                Element parent = item.parent();
                if (parent != null) {
                    img = parent.selectFirst("img");
                    if (img != null) {
                        pic = img.attr("data-original");
                        if (pic.isEmpty()) pic = img.attr("src");
                    }
                }
            }

            // 状态标签
            String remarks = "";
            Element r = item.selectFirst(".pic-text, .pic_text, .remarks, .status, .tag, span[class*=text]");
            if (r != null) remarks = r.text().trim();

            // 使用完整URL作为vod_id，TVBox会传入这个ID到detailContent
            String fullUrl = href.startsWith("http") ? href : SITE_URL + href;
            list.add(new Vod(fullUrl, title, pic, remarks));
        }

        return list;
    }

    /**
     * 从URL提取影片ID
     */
    private String extractIdFromUrl(String url) {
        if (url == null) return "";
        Matcher m = Pattern.compile("/voddetail/(\d+)\.html").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/vodplay/(\d+)-").matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    /**
     * 解析总页数
     */
    private int parseTotalPage(String html) {
        int total = -1;
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href*=/vodshow/], a[href*=/vodsearch/]");
        for (Element link : links) {
            try {
                int n = Integer.parseInt(link.text().trim());
                if (n > total) total = n;
            } catch (Exception ignored) {}
        }
        return total;
    }

    /**
     * 获取Meta标签内容
     */
    private String getMeta(Document doc, String attr, String val) {
        Element e = doc.selectFirst("meta[" + attr + "=" + val + "]");
        return e != null ? e.attr("content").trim() : "";
    }

    /**
     * 解密视频URL
     * 该站点使用多层URL编码加密
     */
    private String decodeVideoUrl(String encodedUrl) {
        if (encodedUrl == null || encodedUrl.isEmpty()) return "";
        String result = encodedUrl;
        for (int i = 0; i < 5; i++) {
            if (!result.contains("%")) break;
            try {
                String dec = URLDecoder.decode(result, "UTF-8");
                if (dec.equals(result)) break;
                result = dec;
            } catch (Exception e) { break; }
        }
        result = result.replace("\/", "/");
        if (!result.startsWith("http")) {
            if (result.startsWith("//")) result = "https:" + result;
            else if (result.startsWith("/")) result = SITE_URL + result;
        }
        return result;
    }
}
