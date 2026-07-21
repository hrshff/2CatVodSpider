package com.github.catvod.spider;

import android.content.Context;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蘑菇影视 (www.5o5k.com) - MacCMS V10 + mxonePro
 * 修复：统一为 Result 工具类风格，修正分页 URL 格式与 hasNext 判断
 */
public class Mogu extends Spider {

    private static final String SITE_URL = "https://www.5o5k.com";

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", SITE_URL + "/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        return headers;
    }

    private String fetch(String url) {
        return OkHttp.string(url, getHeaders());
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return SITE_URL + url;
        return SITE_URL + "/" + url;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("20", "电影"));
        classes.add(new Class("35", "连续剧"));
        classes.add(new Class("43", "综艺"));
        classes.add(new Class("48", "动漫"));
        classes.add(new Class("54", "影视解说"));
        classes.add(new Class("55", "短剧"));
        classes.add(new Class("63", "预告片"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Vod> list = new ArrayList<>();
        String html = fetch(SITE_URL);
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".module-poster-item");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String title = item.attr("title");
            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;
            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            String note = "";
            Element noteEl = item.selectFirst(".module-item-note");
            if (noteEl != null) note = noteEl.text().trim();
            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(note);
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
            url = SITE_URL + "/vodshow/" + tid + "-----------.html";
        } else {
            // MacCMS V10 标准分页格式：页码位于第 9 个字段位
            url = SITE_URL + "/vodshow/" + tid + "--------" + page + "---.html";
        }

        System.out.println("[Mogu-DEBUG] categoryContent url=" + url);
        String html = fetch(url);
        Document doc = Jsoup.parse(html);

        Elements items = doc.select(".module-poster-item");
        for (Element item : items) {
            String href = fixUrl(item.attr("href"));
            String title = item.attr("title");
            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;
            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }
            String note = "";
            Element noteEl = item.selectFirst(".module-item-note");
            if (noteEl != null) note = noteEl.text().trim();
            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(note);
            list.add(vod);
        }

        // 通过分页控件判断是否有下一页
        boolean hasNext = doc.select(".page-link, .pagination a, .stui-page a, .fed-page-info a").size() > 1 || list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = hasNext ? 99999 : page * list.size();

        System.out.println("[Mogu-DEBUG] categoryContent page=" + page + " items=" + list.size() + " hasNext=" + hasNext);
        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);
        if (TextUtils.isEmpty(id)) return "";

        String html = fetch(id);
        Document doc = Jsoup.parse(html);

        Vod vod = new Vod();
        vod.setVodId(id);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) vod.setVodName(h1.text().trim());
        if (TextUtils.isEmpty(vod.getVodName())) {
            Element metaName = doc.selectFirst("meta[itemprop=name]");
            if (metaName != null) vod.setVodName(metaName.attr("content"));
        }

        Element metaImg = doc.selectFirst("meta[itemprop=image]");
        if (metaImg != null) vod.setVodPic(metaImg.attr("content"));

        Element metaDesc = doc.selectFirst("meta[itemprop=description]");
        if (metaDesc != null) vod.setVodContent(metaDesc.attr("content"));

        Element metaActor = doc.selectFirst("meta[itemprop=actor]");
        if (metaActor != null) vod.setVodActor(metaActor.attr("content"));

        Element metaDirector = doc.selectFirst("meta[itemprop=director]");
        if (metaDirector != null) vod.setVodDirector(metaDirector.attr("content"));

        Element metaArea = doc.selectFirst("meta[itemprop=contentLocation]");
        if (metaArea != null) vod.setVodArea(metaArea.attr("content"));

        Element metaClass = doc.selectFirst("meta[itemprop=class]");
        if (metaClass != null) vod.setTypeName(metaClass.attr("content"));

        Element metaDate = doc.selectFirst("meta[itemprop=uploadDate]");
        if (metaDate != null) {
            String date = metaDate.attr("content");
            if (date.length() >= 4) vod.setVodYear(date.substring(0, 4));
        }

        List<String> playFroms = new ArrayList<>();
        List<String> playUrls = new ArrayList<>();

        Elements tabs = doc.select(".module-tab-item");
        Elements playLists = doc.select(".module-play-list");

        for (int i = 0; i < tabs.size(); i++) {
            Element tab = tabs.get(i);
            String sourceName = "";
            Element span = tab.selectFirst("span");
            if (span != null) sourceName = span.text().trim();
            if (TextUtils.isEmpty(sourceName)) sourceName = "源" + (i + 1);

            List<String> urls = new ArrayList<>();
            if (i < playLists.size()) {
                Element playList = playLists.get(i);
                Elements links = playList.select(".module-play-list-link");
                for (Element link : links) {
                    String href = fixUrl(link.attr("href"));
                    String epName = "";
                    Element epSpan = link.selectFirst("span");
                    if (epSpan != null) epName = epSpan.text().trim();
                    if (TextUtils.isEmpty(epName)) epName = link.text().trim();
                    if (!TextUtils.isEmpty(href) && !TextUtils.isEmpty(epName)) {
                        urls.add(epName + "$" + href);
                    }
                }
            }
            if (!urls.isEmpty()) {
                playFroms.add(sourceName);
                playUrls.add(String.join("#", urls));
            }
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

        String html = fetch(id);
        Matcher matcher = Pattern.compile("var player_\\w+\\s*=\\s*\\{.*?\\};", Pattern.DOTALL).matcher(html);
        if (!matcher.find()) {
            return Result.get().url("").string();
        }

        String playerStr = matcher.group();
        Matcher urlMatcher = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(playerStr);
        if (!urlMatcher.find()) {
            return Result.get().url("").string();
        }

        String mediaUrl = urlMatcher.group(1);
        Matcher encMatcher = Pattern.compile("\\\"encrypt\\\"\\s*:\\s*(\\d+)").matcher(playerStr);
        int encrypt = encMatcher.find() ? Integer.parseInt(encMatcher.group(1)) : 0;

        if (encrypt == 1) {
            mediaUrl = java.net.URLDecoder.decode(mediaUrl, "UTF-8");
        }

        boolean isM3u8 = mediaUrl.contains(".m3u8");
        boolean isMp4 = mediaUrl.contains(".mp4");
        int parse = (isM3u8 || isMp4) ? 0 : 1;

        HashMap<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
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
        String url;
        if (page == 1) {
            url = SITE_URL + "/vodsearch/" + encodedKey + "-------------.html";
        } else {
            url = SITE_URL + "/vodsearch/" + encodedKey + "----------" + page + "---.html";
        }

        String html = fetch(url);
        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".module-card-item");

        for (Element item : items) {
            Element a = item.selectFirst("a.module-card-item-poster");
            if (a == null) a = item.selectFirst("a[href^=/voddetail/]");

            String href = a != null ? fixUrl(a.attr("href")) : "";
            String title = "";
            Element titleEl = item.selectFirst(".module-card-item-title");
            if (titleEl != null) title = titleEl.text().trim();
            if (TextUtils.isEmpty(title) && a != null) title = a.attr("title");

            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String note = "";
            Element noteEl = item.selectFirst(".module-item-note");
            if (noteEl != null) note = noteEl.text().trim();

            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

            Vod vod = new Vod();
            vod.setVodId(href);
            vod.setVodName(title);
            vod.setVodPic(fixUrl(pic));
            vod.setVodRemarks(note);
            list.add(vod);
        }

        boolean hasNext = list.size() >= 24;
        int pageCount = hasNext ? page + 1 : page;
        int total = list.isEmpty() ? 0 : list.size();

        return Result.get().vod(list).page(page, pageCount, 24, total).string();
    }
}
