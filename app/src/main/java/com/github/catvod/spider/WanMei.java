package com.github.catvod.spider;

import android.content.Context;
import android.util.Log;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 完美动漫 (www.wmdm.cc)
 * 修复：增加翻页诊断日志，修正 hasNext 与元数据计算
 */
public class WanMei extends Spider {

    private static final String SITE_URL = "https://www.wmdm.cc";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 15; 2407FRK8EC Build/AP3A.240617.008; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        return headers;
    }

    private String fetch(String url) {
        Log.d("WanMei", "[WanMei] HTTP Request: " + url);
        long start = System.currentTimeMillis();
        String html = OkHttp.string(url, getHeaders());
        long cost = System.currentTimeMillis() - start;
        Log.d("WanMei", "[WanMei] HTTP Response: code=200 len=" + (html != null ? html.length() : 0) + " cost=" + cost + "ms");
        return html;
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }



    private String extractId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = Pattern.compile("/content/(\\d+)\\.html").matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("20", "国产动漫"));
        classes.add(new Class("21", "日韩动漫"));
        classes.add(new Class("22", "港台动漫"));
        classes.add(new Class("23", "欧美动漫"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();

        String html = fetch(SITE_URL + "/");
        Document doc = Jsoup.parse(html);

        Elements slides = doc.select(".carousel-item");
        for (Element slide : slides) {
            Element link = slide.selectFirst("a.media-content");
            if (link == null) continue;
            String href = fixUrl(link.attr("href"));
            String id = extractId(href);
            if (TextUtils.isEmpty(id)) continue;

            String title = "";
            Element h4 = link.selectFirst("h4");
            if (h4 != null) title = h4.text().trim();
            if (TextUtils.isEmpty(title)) {
                Element img = link.selectFirst("img");
                if (img != null) title = img.attr("alt");
            }

            String pic = "";
            Element img = link.selectFirst("img");
            if (img != null) {
                pic = img.attr("src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("data-src");
            }

            if (idSet.contains(id)) continue;
            idSet.add(id);

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            list.add(vod);
        }

        Elements items = doc.select("a.media-content");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String id = extractId(href);
            if (TextUtils.isEmpty(id)) continue;
            if (idSet.contains(id)) continue;

            String title = "";
            Element img = item.selectFirst("img");
            if (img != null) title = img.attr("alt");
            if (TextUtils.isEmpty(title)) {
                Element parent = item.parent();
                if (parent != null) {
                    Element h3 = parent.selectFirst("h3 a");
                    if (h3 != null) title = h3.text().trim();
                }
            }
            if (TextUtils.isEmpty(title)) continue;

            String pic = "";
            if (img != null) {
                pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String status = "";
            Element note = item.selectFirst("span.position-absolute");
            if (note != null) status = note.text().trim();

            idSet.add(id);
            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(status);
            list.add(vod);
        }

        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        List<Vod> list = new ArrayList<>();
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }

        String url;
        if (page == 1) {
            url = SITE_URL + "/htmlshow/" + tid + "-----------.html";
        } else {
            url = SITE_URL + "/htmlshow/" + tid + "--------" + page + "---.html";
        }

        try {
            String html = fetch(url);
            if (TextUtils.isEmpty(html)) {
                Log.d("WanMei", "[WanMei-DEBUG] fetch empty, url=" + url);
                return Result.get().vod(list).page(page, page, 24, 0).string();
            }

            Log.d("WanMei", "[WanMei-DEBUG] fetch ok, url=" + url + " len=" + html.length());
            Document doc = Jsoup.parse(html);

            Elements items = doc.select("a.media-content");
            Log.d("WanMei", "[WanMei-DEBUG] items=" + items.size());

            for (Element item : items) {
                String href = fixUrl(item.attr("href"));
                String id = extractId(href);
                if (TextUtils.isEmpty(id)) continue;

                String title = "";
                Element img = item.selectFirst("img");
                if (img != null) title = img.attr("alt");
                if (TextUtils.isEmpty(title)) {
                    Element parent = item.parent();
                    if (parent != null) {
                        Element h3 = parent.selectFirst("h3 a");
                        if (h3 != null) title = h3.text().trim();
                    }
                }
                if (TextUtils.isEmpty(title)) continue;

                String pic = "";
                if (img != null) {
                    pic = img.attr("data-src");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                }

                String status = "";
                Element note = item.selectFirst("span.position-absolute");
                if (note != null) status = note.text().trim();

                Vod vod = new Vod();
                vod.setVodId(id);
                vod.setVodName(title);
                vod.setVodPic(fixUrl(pic));
                vod.setVodRemarks(status);
                list.add(vod);
            }

            boolean hasNext = doc.select("a[href*=htmlshow]").size() > items.size() || list.size() >= 24;
            int pageCount = hasNext ? page + 1 : page;
            int total = hasNext ? 99999 : list.size();

            if (!list.isEmpty()) {
                list.get(0).setVodRemarks("url=" + url.replace(SITE_URL, "") + "|items=" + list.size() + "|hasNext=" + hasNext);
            }

            Log.d("WanMei", "[WanMei-DEBUG] return items=" + list.size() + " pageCount=" + pageCount);
            return Result.get().vod(list).page(page, pageCount, 24, total).string();

        } catch (Exception e) {
            Log.d("WanMei", "[WanMei] Exception: " + e.getClass().getSimpleName() + " " + e.getMessage());
            // 异常信息带上下文，TVBox 界面会显示，方便用户截图定位
            throw new Exception("[WanMei] 分类获取失败: tid=" + tid + ", page=" + page + ", url=" + url + ", 原因=" + e.getMessage(), e);
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        String detailUrl = SITE_URL + "/content/" + id + ".html";
        String html = fetch(detailUrl);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());

        String pic = "";
        Element ogImg = doc.selectFirst("meta[property=og:image]");
        if (ogImg != null) {
            pic = ogImg.attr("content");
            if (pic.contains(SITE_URL + "http")) {
                pic = pic.replace(SITE_URL, "");
            }
        }
        if (TextUtils.isEmpty(pic)) {
            Element img = doc.selectFirst(".media-content img");
            if (img != null) {
                pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
        }
        vod.setVodPic(fixUrl(pic));

        String director = "";
        String actor = "";
        String typeName = "";
        String area = "";
        String year = "";

        Elements infoItems = doc.select(".hl-info li, .info li");
        for (Element item : infoItems) {
            String text = item.text();
            if (text.contains("导演")) {
                director = text.replace("导演：", "").replace("导演:", "").trim();
            } else if (text.contains("主演")) {
                actor = text.replace("主演：", "").replace("主演:", "").trim();
            } else if (text.contains("类型")) {
                typeName = text.replace("类型：", "").replace("类型:", "").trim();
            } else if (text.contains("地区")) {
                area = text.replace("地区：", "").replace("地区:", "").trim();
            } else if (text.contains("年份") || text.contains("年代")) {
                year = text.replace("年份：", "").replace("年份:", "").replace("年代：", "").trim();
            }
        }

        if (TextUtils.isEmpty(director) || TextUtils.isEmpty(actor)) {
            for (Element li : doc.select("li")) {
                String text = li.text();
                if (text.contains("导演") && TextUtils.isEmpty(director)) {
                    Element a = li.selectFirst("a");
                    if (a != null) director = a.text().trim();
                }
                if (text.contains("主演") && TextUtils.isEmpty(actor)) {
                    StringBuilder sb = new StringBuilder();
                    for (Element a : li.select("a")) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(a.text().trim());
                    }
                    if (sb.length() > 0) actor = sb.toString();
                }
            }
        }

        vod.setVodDirector(director);
        vod.setVodActor(actor);
        vod.setTypeName(typeName);
        vod.setVodArea(area);
        vod.setVodYear(year);

        Element descEl = doc.selectFirst("meta[name=description]");
        if (descEl != null) {
            String desc = descEl.attr("content");
            if (desc.contains("剧情：")) {
                desc = desc.substring(desc.indexOf("剧情：") + 3);
            }
            vod.setVodContent(desc.trim());
        }

        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements tabLinks = doc.select(".nav-urls a");
        List<String> tabNames = new ArrayList<>();
        for (Element tab : tabLinks) {
            String name = tab.text().trim();
            if (!TextUtils.isEmpty(name) && !name.contains("选集") && !name.contains("剧情")) {
                tabNames.add(name);
            }
        }

        Elements playlists = doc.select(".v-playurl .hl-plays-list");
        if (playlists.isEmpty()) {
            playlists = doc.select(".hl-plays-list");
        }

        for (int i = 0; i < playlists.size(); i++) {
            Element playlist = playlists.get(i);
            String tabName = (i < tabNames.size()) ? tabNames.get(i) : ("线路" + (i + 1));
            playFroms.add(tabName);

            Elements links = playlist.select("a");
            List<String> urls = new ArrayList<>();
            for (Element link : links) {
                String name = link.text().trim();
                String href = fixUrl(link.attr("href"));
                if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(href)) {
                    urls.add(name + "$" + href);
                }
            }
            playUrls.add(String.join("#", urls));
        }

        vod.setVodPlayFrom(String.join("$$$", playFroms));
        vod.setVodPlayUrl(String.join("$$$", playUrls));

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(id)) {
            return Result.get().url("").string();
        }

        String playUrl = id.startsWith("http") ? id : fixUrl(id);
        String html = fetch(playUrl);
        Document doc = Jsoup.parse(html);

        String url = "";
        Element playerDiv = doc.selectFirst(".video-iframe");
        if (playerDiv != null) {
            url = playerDiv.attr("data-play");
        }

        HashMap<String, String> header = getHeaders();
        header.put("Origin", SITE_URL);
        header.put("Referer", playUrl);

        if (flag.contains("YZ") || flag.contains("yz")) {
            return Result.get().url(playUrl).parse(1).header(header).string();
        }

        if (!TextUtils.isEmpty(url)) {
            boolean isM3u8 = url.contains(".m3u8");
            boolean isMp4 = url.contains(".mp4");
            if (isM3u8 || isMp4) {
                return Result.get().url(url).parse(0).header(header).string();
            }
        }

        Element iframe = doc.selectFirst("iframe");
        if (iframe != null) {
            String src = fixUrl(iframe.attr("src"));
            return Result.get().url(src).parse(1).header(header).string();
        }

        return Result.get().url(playUrl).parse(1).header(header).string();
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
        String url = SITE_URL + "/dmso/so.html?wd=" + encodedKey;
        if (page > 1) {
            url = url + "&page=" + page;
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        Elements items = doc.select("a.media-content");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String id = extractId(href);
            if (TextUtils.isEmpty(id)) continue;

            String title = "";
            Element img = item.selectFirst("img");
            if (img != null) title = img.attr("alt");
            if (TextUtils.isEmpty(title)) {
                Element parent = item.parent();
                if (parent != null) {
                    Element next = parent.nextElementSibling();
                    if (next != null) {
                        Element h3 = next.selectFirst("h3 a, a");
                        if (h3 != null) title = h3.text().trim();
                    }
                }
            }
            if (TextUtils.isEmpty(title)) continue;

            String pic = "";
            if (img != null) {
                pic = img.attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            Vod vod = new Vod();
            vod.setVodId(id);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            list.add(vod);
        }

        boolean hasNext = doc.select(".page-link, .pagination a, .page-list a").size() > 0 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.isEmpty() ? 0 : list.size();

        // 调试信息：放入第一个条目的备注中（如果列表不为空）
        if (!list.isEmpty()) {
            list.get(0).setVodRemarks("url=" + url.replace(SITE_URL, "") + "|items=" + list.size() + "|hasNext=" + hasNext);
        }
        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }
}
