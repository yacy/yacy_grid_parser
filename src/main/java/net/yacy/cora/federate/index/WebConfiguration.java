/**
 *  CollectionConfiguration
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.federate.index;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.document.DateDetection;
import net.yacy.document.Document;
import net.yacy.document.SentenceReader;
import net.yacy.document.parser.html.Scraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.tools.AnchorURL;
import net.yacy.grid.tools.Classification.ContentDomain;
import net.yacy.grid.tools.CommonPattern;
import net.yacy.grid.tools.DateParser;
import net.yacy.grid.tools.MultiProtocolURL;


public class WebConfiguration implements Serializable {

    private static final long serialVersionUID=-499100932212840385L;

    public static boolean UNIQUE_HEURISTIC_PREFER_HTTPS = false;
    public static boolean UNIQUE_HEURISTIC_PREFER_WWWPREFIX = true;
    
    private static void add(JSONObject json, WebMapping field, String value) {
        json.put(field.getMapping().name(), value);
    }
    private static void add(JSONObject json, WebMapping field, int value) {
        json.put(field.getMapping().name(), (long) value);
    }
    private static void add(JSONObject json, WebMapping field, double value) {
        json.put(field.getMapping().name(), value);
    }
    private static void add(JSONObject json, WebMapping field, boolean value) {
        json.put(field.getMapping().name(), value);
    }
    private static void add(JSONObject json, WebMapping field, Date value) {
        json.put(field.getMapping().name(), DateParser.iso8601MillisFormat.format(value));
    }
    private static void add(JSONObject json, WebMapping field, String[] values) {
        JSONArray a = new JSONArray();
        for (String s: values) a.put(s);
        json.put(field.getMapping().name(), a);
    }
    private static void add(JSONObject json, WebMapping field, Integer[] values) {
        JSONArray a = new JSONArray();
        for (Integer s: values) a.put((long) s);
        json.put(field.getMapping().name(), a);
    }
    private static void add(JSONObject json, WebMapping field, Date[] values) {
        JSONArray a = new JSONArray();
        for (Date s: values) a.put(DateParser.iso8601MillisFormat.format(s));
        json.put(field.getMapping().name(), a);
    }
    private static void add(JSONObject json, WebMapping field, List<?> values) {
        JSONArray a = new JSONArray();
        for (Object s: values) {
            if (s instanceof Date) a.put(DateParser.iso8601MillisFormat.format((Date) s));
            else a.put(s);
        }
        json.put(field.getMapping().name(), a);
    }
    private static void add(JSONObject json, WebMapping field, JSONObject object) {
        json.put(field.getMapping().name(), object);
    }

    public static class Subgraph {
        public final ArrayList<String>[] urls;
        public final ArrayList<String>[] urlAnchorTexts;
        @SuppressWarnings("unchecked")
        public Subgraph(int inboundSize, int outboundSize) {
            this.urls = (ArrayList<String>[]) Array.newInstance(ArrayList.class, 2);
            this.urls[0] = new ArrayList<String>(inboundSize);
            this.urls[1] = new ArrayList<String>(outboundSize);
            this.urlAnchorTexts = (ArrayList<String>[]) Array.newInstance(ArrayList.class, 2);
            this.urlAnchorTexts[0] = new ArrayList<String>(inboundSize);
            this.urlAnchorTexts[1] = new ArrayList<String>(outboundSize);
        }
    }

    public static boolean enrichSubgraph(final Subgraph subgraph, final MultiProtocolURL source_url, AnchorURL target_url) {
        final String text = target_url.getTextProperty(); // the text between the <a></a> tag
        String source_host = source_url.getHost();
        String target_host = target_url.getHost();
        boolean inbound =
                (source_host == null && target_host == null) || 
                (source_host != null && target_host != null &&
                 (target_host.equals(source_host) ||
                  target_host.equals("www." + source_host) ||
                  source_host.equals("www." + target_host))); // well, not everybody defines 'outbound' that way but however, thats used here.
        int ioidx = inbound ? 0 : 1;
        subgraph.urls[ioidx].add(target_url.toNormalform(true));
        subgraph.urlAnchorTexts[ioidx].add(text);
        return inbound;
    }
    
    /**
     * add uri attributes to json document and assign the document id
     * @param doc
     * @param allAttr
     * @param MultiProtocolURL used to calc. the document.id and the doc.sku=(in index stored url)
     * @return the normalized url
     */
    public static String addURIAttributes(final JSONObject doc, final MultiProtocolURL MultiProtocolURL) {
        String us = MultiProtocolURL.toNormalform(true);
        add(doc, WebMapping.url_s, us);
        final InetAddress address = MultiProtocolURL.getInetAddress();
        if (address != null) add(doc, WebMapping.ip_s, address.getHostAddress());

        String host = null;
        if ((host = MultiProtocolURL.getHost()) != null) {
            String dnc = Domains.getDNC(host);
            String subdomOrga = host.length() - dnc.length() <= 0 ? "" : host.substring(0, host.length() - dnc.length() - 1);
            int p = subdomOrga.lastIndexOf('.');
            String subdom = (p < 0) ? "" : subdomOrga.substring(0, p);
            String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
            add(doc, WebMapping.host_s, host);
            add(doc, WebMapping.host_dnc_s, dnc);
            add(doc, WebMapping.host_organization_s, orga);
            add(doc, WebMapping.host_organizationdnc_s, orga + '.' + dnc);
            add(doc, WebMapping.host_subdomain_s, subdom);
        }
        
        // path elements of link
        String filename = MultiProtocolURL.getFileName();
        String extension = net.yacy.grid.tools.MultiProtocolURL.getFileExtension(filename);
        String filenameStub = filename.toLowerCase().endsWith("." + extension) ? filename.substring(0, filename.length() - extension.length() - 1) : filename;
        // remove possible jsession (or other url parm like "img.jpg;jsession=123") 
        // TODO: consider to implement ";jsession=123" check in getFileExtension()
        if (extension.indexOf(';') >= 0) extension = extension.substring(0,extension.indexOf(';'));
        
        add(doc, WebMapping.url_chars_i, us.length());
        add(doc, WebMapping.url_protocol_s, MultiProtocolURL.getProtocol());
        
        String[] paths = MultiProtocolURL.getPaths();
        add(doc, WebMapping.url_paths_count_i, paths.length);
        add(doc, WebMapping.url_paths_sxt, paths);
        
        add(doc, WebMapping.url_file_name_s, filenameStub);
        add(doc, WebMapping.url_file_name_tokens_t, net.yacy.grid.tools.MultiProtocolURL.toTokens(filenameStub));
        add(doc, WebMapping.url_file_ext_s, extension);
        
        Map<String, String> searchpart = MultiProtocolURL.getSearchpartMap();
        if (searchpart == null) {
            add(doc, WebMapping.url_parameter_i, 0);
        } else {
            add(doc, WebMapping.url_parameter_i, searchpart.size());
            add(doc, WebMapping.url_parameter_key_sxt, searchpart.keySet().toArray(new String[searchpart.size()]));
            add(doc, WebMapping.url_parameter_value_sxt,  searchpart.values().toArray(new String[searchpart.size()]));
        }
        return us;
    }

    public static JSONObject yacy2solr(
            final Map<String, Pattern> collections, final ResponseHeader responseHeader,
            final Document document, final MultiProtocolURL referrerURL, final String language, final boolean setUnique,
            int timezoneOffset) {
        // we use the SolrCell design as index schema
        final MultiProtocolURL digestURL = document.dc_source();
        JSONObject doc = new JSONObject(true);
        String url = addURIAttributes(doc, digestURL);
        add(doc, WebMapping.content_type, new String[]{document.dc_format()}); // content_type (mime) is defined a schema field and we rely on it in some queries like imagequery (makes it mandatory, no need to check)

        String host = digestURL.getHost();
        
        int crawldepth = document.getDepth();
        add(doc, WebMapping.crawldepth_i, crawldepth);
      
        if (collections != null && collections.size() > 0) {
            List<String> cs = new ArrayList<String>();
            for (Map.Entry<String, Pattern> e: collections.entrySet()) {
                if (e.getValue().matcher(url).matches()) cs.add(e.getKey());
            }
            add(doc, WebMapping.collection_sxt, cs);
        }

        List<String> titles = document.titles();
        add(doc, WebMapping.title, titles);
        add(doc, WebMapping.title_count_i, titles.size());
        ArrayList<Integer> cv = new ArrayList<Integer>(titles.size());
        for (String s: titles) cv.add(new Integer(s.length()));
        add(doc, WebMapping.title_chars_val, cv);
        
        cv = new ArrayList<Integer>(titles.size());
        for (String s: titles) cv.add(new Integer(CommonPattern.SPACES.split(s).length));
        add(doc, WebMapping.title_words_val, cv);

        String[] descriptions = document.dc_description();
        add(doc, WebMapping.description_txt, descriptions);

        add(doc, WebMapping.description_count_i, descriptions.length);
        cv = new ArrayList<Integer>(descriptions.length);
        for (String s: descriptions) cv.add(new Integer(s.length()));
        add(doc, WebMapping.description_chars_val, cv);
        
        cv = new ArrayList<Integer>(descriptions.length);
        for (String s: descriptions) cv.add(new Integer(CommonPattern.SPACES.split(s).length));
        add(doc, WebMapping.description_words_val, cv);

        String author = document.dc_creator();
        if (author == null || author.length() == 0) author = document.dc_publisher();
        if (author != null && author.length() > 0) {
            add(doc, WebMapping.author, author);
            add(doc, WebMapping.author_sxt, new String[]{author}); // this was a copy-field when used with solr
        }
        
        Date lastModified = responseHeader == null ? new Date() : responseHeader.lastModified();
        if (lastModified == null) lastModified = new Date();
        if (document.getLastModified().before(lastModified)) lastModified = document.getLastModified();
        add(doc, WebMapping.last_modified, lastModified);

        String content = document.getTextString();

        LinkedHashSet<Date> dates_in_content = DateDetection.parse(content, timezoneOffset);
       
        add(doc, WebMapping.dates_in_content_count_i, dates_in_content.size());
            
        add(doc, WebMapping.dates_in_content_dts, dates_in_content.toArray(new Date[dates_in_content.size()]));

        String keywords = document.dc_subject(' ');
        add(doc, WebMapping.keywords, keywords);

        // unique-fields; these values must be corrected during postprocessing. (the following logic is !^ (not-xor) but I prefer to write it that way as it is)
        add(doc, WebMapping.http_unique_b, setUnique || UNIQUE_HEURISTIC_PREFER_HTTPS ? digestURL.isHTTPS() : digestURL.isHTTP()); // this must be corrected afterwards during storage!
        add(doc, WebMapping.www_unique_b, setUnique || host != null && (UNIQUE_HEURISTIC_PREFER_WWWPREFIX ? host.startsWith("www.") : !host.startsWith("www."))); // this must be corrected afterwards during storage!
        
        // get list of all links; they will be shrinked by urls that appear in other fields of the solr schema
        LinkedHashMap<MultiProtocolURL,String> inboundLinks = document.inboundLinks();
        LinkedHashMap<MultiProtocolURL,String> outboundLinks = document.outboundLinks();

        int c = 0;
        final Object scraper = document.getScraperObject();
        MultiProtocolURL canonical = null;
        
        if (scraper instanceof Scraper) {
            final Scraper html = (Scraper) scraper;
            List<ImageEntry> images = html.getImages();

            // header tags
            int h = 0;
            int f = 1;
            String[] hs;

            hs = html.getHeadlines(1); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h1_txt, hs); add(doc, WebMapping.h1_i, hs.length);
            hs = html.getHeadlines(2); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h2_txt, hs); add(doc, WebMapping.h2_i, hs.length);
            hs = html.getHeadlines(3); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h3_txt, hs); add(doc, WebMapping.h3_i, hs.length);
            hs = html.getHeadlines(4); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h4_txt, hs); add(doc, WebMapping.h4_i, hs.length);
            hs = html.getHeadlines(5); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h5_txt, hs); add(doc, WebMapping.h5_i, hs.length);
            hs = html.getHeadlines(6); h = h | (hs.length > 0 ? f : 0); f = f * 2; add(doc, WebMapping.h6_txt, hs); add(doc, WebMapping.h6_i, hs.length);
       
            add(doc, WebMapping.htags_i, h);
            add(doc, WebMapping.schema_org_breadcrumb_i, html.breadcrumbCount());

            // meta tags: Open Graph properties
            String og;
            og = html.getMetas().get("og:title"); if (og != null) add(doc, WebMapping.opengraph_title_t, og);
            og = html.getMetas().get("og:type"); if (og != null) add(doc, WebMapping.opengraph_type_s, og);
            og = html.getMetas().get("og:url"); if (og != null) add(doc, WebMapping.opengraph_url_s, og);
            og = html.getMetas().get("og:image"); if (og != null) add(doc, WebMapping.opengraph_image_s, og);

            // noindex and nofollow attributes
            // from HTML (meta-tag in HTML header: robots)
            // and HTTP header (X-Robots-Tag property)
            // coded as binary value:
            // bit  0: "all" contained in html header meta
            // bit  1: "index" contained in html header meta
            // bit  2: "follow" contained in html header meta
            // bit  3: "noindex" contained in html header meta
            // bit  4: "nofollow" contained in html header meta
            // bit  5: "noarchive" contained in html header meta
            // bit  8: "all" contained in http header X-Robots-Tag
            // bit  9: "noindex" contained in http header X-Robots-Tag
            // bit 10: "nofollow" contained in http header X-Robots-Tag
            // bit 11: "noarchive" contained in http header X-Robots-Tag
            // bit 12: "nosnippet" contained in http header X-Robots-Tag
            // bit 13: "noodp" contained in http header X-Robots-Tag
            // bit 14: "notranslate" contained in http header X-Robots-Tag
            // bit 15: "noimageindex" contained in http header X-Robots-Tag
            // bit 16: "unavailable_after" contained in http header X-Robots-Tag
            int b = 0;
            String robots_meta = html.getMetas().get("robots");
            // this tag may have values: all, index, noindex, nofollow; see http://www.robotstxt.org/meta.html
            if (robots_meta != null) {
                robots_meta = robots_meta.toLowerCase();
                if (robots_meta.indexOf("all",0) >= 0) b += 1;      // set bit 0
                if (robots_meta.indexOf("index",0) == 0 || robots_meta.indexOf(" index",0) >= 0 || robots_meta.indexOf(",index",0) >= 0 ) b += 2; // set bit 1
                if (robots_meta.indexOf("follow",0) == 0 || robots_meta.indexOf(" follow",0) >= 0 || robots_meta.indexOf(",follow",0) >= 0 ) b += 4; // set bit 2
                if (robots_meta.indexOf("noindex",0) >= 0) b += 8;  // set bit 3
                if (robots_meta.indexOf("nofollow",0) >= 0) b += 16; // set bit 4
                if (robots_meta.indexOf("noarchive",0) >= 0) b += 32; // set bit 5
            }
            String x_robots_tag = responseHeader == null ? "" : responseHeader.getXRobotsTag();
            if (!x_robots_tag.isEmpty()) {
                // this tag may have values: all, noindex, nofollow, noarchive, nosnippet, noodp, notranslate, noimageindex, unavailable_after, none; see https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag?hl=de
                if (x_robots_tag.indexOf("all",0) >= 0) b += 1<<8;                // set bit 8
                if (x_robots_tag.indexOf("noindex",0) >= 0||x_robots_tag.indexOf("none",0) >= 0) b += 1<<9;   // set bit 9
                if (x_robots_tag.indexOf("nofollow",0) >= 0||x_robots_tag.indexOf("none",0) >= 0) b += 1<<10; // set bit 10
                if (x_robots_tag.indexOf("noarchive",0) >= 0) b += 1<<11;         // set bit 11
                if (x_robots_tag.indexOf("nosnippet",0) >= 0) b += 1<<12;         // set bit 12
                if (x_robots_tag.indexOf("noodp",0) >= 0) b += 1<<13;             // set bit 13
                if (x_robots_tag.indexOf("notranslate",0) >= 0) b += 1<<14;       // set bit 14
                if (x_robots_tag.indexOf("noimageindex",0) >= 0) b += 1<<15;      // set bit 15
                if (x_robots_tag.indexOf("unavailable_after",0) >= 0) b += 1<<16; // set bit 16
            }
            add(doc, WebMapping.robots_i, b);

            // meta tags: generator
            final String generator = html.getMetas().get("generator");
            if (generator != null) add(doc, WebMapping.metagenerator_t, generator);

            // bold, italic
            final String[] bold = html.getBold();
            add(doc, WebMapping.boldcount_i, bold.length);
            if (bold.length > 0) {
                add(doc, WebMapping.bold_txt, bold);
                add(doc, WebMapping.bold_val, html.getBoldCount(bold));
            }
            final String[] italic = html.getItalic();
            add(doc, WebMapping.italiccount_i, italic.length);
            if (italic.length > 0) {
                add(doc, WebMapping.italic_txt, italic);
                add(doc, WebMapping.italic_val, html.getItalicCount(italic));
            }
            final String[] underline = html.getUnderline();
            add(doc, WebMapping.underlinecount_i, underline.length);
            if (underline.length > 0) {
                add(doc, WebMapping.underline_txt, underline);
                add(doc, WebMapping.underline_val, html.getUnderlineCount(underline));
            }
            final String[] li = html.getLi();
            add(doc, WebMapping.licount_i, li.length);
            if (li.length > 0) add(doc, WebMapping.li_txt, li);
            
            final String[] dt = html.getDt();
            add(doc, WebMapping.dtcount_i, dt.length);
            if (dt.length > 0) add(doc, WebMapping.dt_txt, dt);

            final String[] dd = html.getDd();
            add(doc, WebMapping.ddcount_i, dd.length);
            if (dd.length > 0) add(doc, WebMapping.dd_txt, dd);

            final List<Date> startDates = html.getStartDates();
            if (startDates.size() > 0) add(doc, WebMapping.startDates_dts, startDates.toArray(new Date[startDates.size()]));
            final List<Date> endDates = html.getEndDates();
            if (endDates.size() > 0) add(doc, WebMapping.endDates_dts, endDates.toArray(new Date[endDates.size()]));
            
            final List<String> articles = html.getArticles();
            add(doc, WebMapping.articlecount_i, articles.size());
            if (articles.size() > 0) add(doc, WebMapping.article_txt, articles);

            // images
            processImages(doc, inboundLinks, outboundLinks, images);

            // style sheets
            final Map<MultiProtocolURL, String> csss = html.getCSS();
            final String[] css_tag = new String[csss.size()];
            final String[] css_url = new String[csss.size()];
            c = 0;
            for (final Map.Entry<MultiProtocolURL, String> entry: csss.entrySet()) {
                final String cssurl = entry.getKey().toNormalform(false);
                inboundLinks.remove(entry.getKey());
                outboundLinks.remove(entry.getKey());
                css_tag[c] =
                    "<link rel=\"stylesheet\" type=\"text/css\" media=\"" + entry.getValue() + "\"" +
                    " href=\""+ cssurl + "\" />";
                css_url[c] = cssurl;
                c++;
            }
            add(doc, WebMapping.csscount_i, css_tag.length);
            if (css_tag.length > 0) add(doc, WebMapping.css_tag_sxt, css_tag);
            if (css_url.length > 0) add(doc, WebMapping.css_url_sxt, css_url);

            // Scripts
            final Set<AnchorURL> scriptss = html.getScript();
            final String[] scripts = new String[scriptss.size()];
            c = 0;
            for (final AnchorURL u: scriptss) {
                inboundLinks.remove(u);
                outboundLinks.remove(u);
                scripts[c++] = u.toNormalform(false);
            }
            add(doc, WebMapping.scriptscount_i, scripts.length);
            if (scripts.length > 0) add(doc, WebMapping.scripts_sxt, scripts);

            // Frames
            final Set<AnchorURL> framess = html.getFrames();
            final String[] frames = new String[framess.size()];
            c = 0;
            for (final AnchorURL u: framess) {
                inboundLinks.remove(u);
                outboundLinks.remove(u);
                frames[c++] = u.toNormalform(false);
            }
            add(doc, WebMapping.framesscount_i, frames.length);
            if (frames.length > 0) {
                add(doc, WebMapping.frames_sxt, frames);
                //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, framess, citations); // add here because links have been removed from remaining inbound/outbound
            }

            // IFrames
            final Set<AnchorURL> iframess = html.getIFrames();
            final String[] iframes = new String[iframess.size()];
            c = 0;
            for (final AnchorURL u: iframess) {
                inboundLinks.remove(u);
                outboundLinks.remove(u);
                iframes[c++] = u.toNormalform(false);
            }
            add(doc, WebMapping.iframesscount_i, iframes.length);
            if (iframes.length > 0) {
                add(doc, WebMapping.iframes_sxt, iframes);
                //webgraph.addEdges(subgraph, digestURI, responseHeader, collections, crawldepth, alllinks, images, true, iframess, citations); // add here because links have been removed from remaining inbound/outbound
            }

            // canonical tag
            canonical = html.getCanonical();
            // if there is no canonical in the html then look into the http header:
            if (canonical == null && responseHeader != null) {
                String link = responseHeader.get("Link", null);
                int p;
                if (link != null && ((p = link.indexOf("rel=\"canonical\"")) > 0)) {
                    link = link.substring(0, p).trim();
                    p = link.indexOf('<');
                    int q = link.lastIndexOf('>');
                    if (p >= 0 && q > 0) {
                        link = link.substring(p + 1, q);
                        try {
                            canonical = new MultiProtocolURL(link);
                        } catch (MalformedURLException e) {}
                    }
                }
            }
            if (canonical != null) {
                inboundLinks.remove(canonical);
                outboundLinks.remove(canonical);
                add(doc, WebMapping.canonical_s, canonical.toNormalform(false));
                // set a flag if this is equal to sku
                add(doc, WebMapping.canonical_equal_sku_b, canonical.equals(digestURL));
            }

            // meta refresh tag
            String refresh = html.getRefreshPath();
            if (refresh != null && refresh.length() > 0) {
                MultiProtocolURL refreshURL;
                try {
                    refreshURL = refresh.startsWith("http") ? new MultiProtocolURL(html.getRefreshPath()) : new MultiProtocolURL(digestURL, html.getRefreshPath());
                    if (refreshURL != null) {
                        inboundLinks.remove(refreshURL);
                        outboundLinks.remove(refreshURL);
                        add(doc, WebMapping.refresh_s, refreshURL.toNormalform(false));
                    }
                } catch (final MalformedURLException e) {
                    add(doc, WebMapping.refresh_s, refresh);
                }
            }

            // flash embedded
            MultiProtocolURL[] flashURLs = html.getFlash();
            for (MultiProtocolURL u: flashURLs) {
                // remove all flash links from ibound/outbound links
                inboundLinks.remove(u);
                outboundLinks.remove(u);
            }
            add(doc, WebMapping.flash_b, flashURLs.length > 0);

            // generic evaluation pattern
            for (final String model: html.getEvaluationModelNames()) {
            final String[] scorenames = html.getEvaluationModelScoreNames(model);
                if (scorenames.length > 0) {
                    add(doc, WebMapping.valueOf("ext_" + model + "_txt"), scorenames);
                    add(doc, WebMapping.valueOf("ext_" + model + "_val"), html.getEvaluationModelScoreCounts(model, scorenames));
                }
            }

            // response time
            add(doc, WebMapping.responsetime_i, responseHeader == null ? 0 : Integer.parseInt(responseHeader.get(HeaderFramework.RESPONSE_TIME_MILLIS, "0")));
            
            // hreflang link tag, see http://support.google.com/webmasters/bin/answer.py?hl=de&answer=189077
            final String[] ccs = new String[html.getHreflang().size()];
            String[] urls = new String[html.getHreflang().size()];
            c = 0;
            for (Map.Entry<String, MultiProtocolURL> e: html.getHreflang().entrySet()) {
                ccs[c] = e.getKey();
                urls[c] = e.getValue().toNormalform(true);
                c++;
            }
            add(doc, WebMapping.hreflang_cc_sxt, ccs);
            add(doc, WebMapping.hreflang_url_sxt, urls);

            // page navigation url, see http://googlewebmastercentral.blogspot.de/2011/09/pagination-with-relnext-and-relprev.html
            final String[] navs = new String[html.getNavigation().size()];
            urls = new String[html.getNavigation().size()];
            c = 0;
            for (Map.Entry<String, MultiProtocolURL> e: html.getNavigation().entrySet()) {
                navs[c] = e.getKey();
                urls[c] = e.getValue().toNormalform(true);
                c++;
            }
            add(doc, WebMapping.navigation_type_sxt, navs);
            add(doc, WebMapping.navigation_url_sxt, urls);

            // publisher url as defined in http://support.google.com/plus/answer/1713826?hl=de
            if (html.getPublisherLink() != null) {
                add(doc, WebMapping.publisher_url_s, html.getPublisherLink().toNormalform(true));
            }
        }

        // handle image source meta data
        if (document.getContentDomain() == ContentDomain.IMAGE) {
            // add image pixel size if known
            Iterator<ImageEntry> imgit = document.getImages().values().iterator();
            List<Integer> heights = new ArrayList<>();
            List<Integer> widths = new ArrayList<>();
            List<Integer> pixels = new ArrayList<>();
            while (imgit.hasNext()) {
                ImageEntry img = imgit.next();
                int imgpixels = (img.height() < 0 || img.width() < 0) ? -1 : img.height() * img.width();
                if (imgpixels > 0) {
                    heights.add(img.height());
                    widths.add(img.width());
                    pixels.add(imgpixels);
                }
            }
            if (heights.size() > 0) {
                add(doc, WebMapping.images_height_val, heights);
                add(doc, WebMapping.images_width_val, widths);
                add(doc, WebMapping.images_pixel_val, pixels);
            }

            add(doc, WebMapping.images_text_t, content); // the content may contain the exif data from the image parser
            content = digestURL.toTokens(); // remove all other entry but the url tokens
        }

        // content (must be written after special parser data, since this can influence the content)
        add(doc, WebMapping.text_t, content);
        if (content.length() == 0) {
            add(doc, WebMapping.wordcount_i, 0);
        } else {
            int contentwc = 1;
            for (int i = content.length() - 1; i >= 0; i--) if (content.charAt(i) == ' ') contentwc++;
            add(doc, WebMapping.wordcount_i, contentwc);
        }
        
        // statistics about the links
        add(doc, WebMapping.linkscount_i, inboundLinks.size() + outboundLinks.size());
        add(doc, WebMapping.linksnofollowcount_i, document.inboundLinkNofollowCount() + document.outboundLinkNofollowCount());
        add(doc, WebMapping.inboundlinkscount_i, inboundLinks.size());
        add(doc, WebMapping.inboundlinksnofollowcount_i, document.inboundLinkNofollowCount());
        add(doc, WebMapping.outboundlinkscount_i, outboundLinks.size());
        add(doc, WebMapping.outboundlinksnofollowcount_i, document.outboundLinkNofollowCount());

        
        // create a subgraph
        Subgraph subgraph = new Subgraph(inboundLinks.size(), outboundLinks.size());
        for (final AnchorURL target_url: document.getHyperlinks().keySet()) {
            enrichSubgraph(subgraph, digestURL, target_url);
        }
       
        // attach the subgraph content
        add(doc, WebMapping.inboundlinks_sxt, subgraph.urls[0]);
        add(doc, WebMapping.inboundlinks_anchortext_txt, subgraph.urlAnchorTexts[0]);
        add(doc, WebMapping.outboundlinks_sxt, subgraph.urls[1]);
        add(doc, WebMapping.outboundlinks_anchortext_txt, subgraph.urlAnchorTexts[1]);
        
        // charset
        add(doc, WebMapping.charset_s, document.getCharset());

        // coordinates
        if (document.lat() != 0.0 && document.lon() != 0.0) {
            add(doc, WebMapping.coordinate_p, Double.toString(document.lat()) + "," + Double.toString(document.lon()));
            add(doc, WebMapping.coordinate_lat_d, document.lat());
            add(doc, WebMapping.coordinate_lon_d, document.lon());
        }
        add(doc, WebMapping.httpstatus_i, responseHeader == null ? 200 : responseHeader.getStatusCode());

        // fields that were additionally in URIMetadataRow
        Date loadDate = new Date();
        Date modDate = responseHeader == null ? new Date() : responseHeader.lastModified();
        if (modDate.getTime() > loadDate.getTime()) modDate = loadDate;
        int size = (int) Math.max(document.dc_source().length(), responseHeader == null ? 0 : responseHeader.getContentLength());
        add(doc, WebMapping.load_date_dt, loadDate);
        add(doc, WebMapping.fresh_date_dt, new Date(loadDate.getTime() + Math.max(0, loadDate.getTime() - modDate.getTime()) / 2)); // freshdate, computed with Proxy-TTL formula
        if (referrerURL != null) add(doc, WebMapping.referrer_url_s, referrerURL.toNormalform(true));
        add(doc, WebMapping.publisher_t, document.dc_publisher());
        if (language != null) add(doc, WebMapping.language_s, language);
        add(doc, WebMapping.size_i, size);
        add(doc, WebMapping.audiolinkscount_i, document.getAudiolinks().size());
        add(doc, WebMapping.videolinkscount_i, document.getVideolinks().size());
        add(doc, WebMapping.applinkscount_i, document.getApplinks().size());

        // LSON-LD object
        JSONObject ld = document.ld();
        String[] keys = ld.keySet().stream().toArray(String[]::new);
        for (String key: keys) {
            if (key.length() > 0 && key.charAt(0) == '@') {
                ld.put(key.substring(1), ld.get(key));
                ld.remove(key);
            }
        }
        //System.out.println("**** LD for " + digestURL.toNormalform(true) + "\n" + ld.toString(2) + "\n"); // debug
        add(doc, WebMapping.ld_o, ld);
        return doc;
    }
    
    /**
     * Add images metadata to Solr doc when corresponding schema attributes are enabled. 
     * Remove images urls from inboudLinks and outboundLinks.
     * @param doc solr document to fill
     * @param allAttr all attributes are enabled
     * @param inboundLinks all document inbound links
     * @param outboundLinks all document outbound links
     * @param images document images
     */
	private static void processImages(JSONObject doc, LinkedHashMap<MultiProtocolURL, String> inboundLinks,
			LinkedHashMap<MultiProtocolURL, String> outboundLinks, List<ImageEntry> images) {
		final ArrayList<String> imgurls = new ArrayList<String>(images.size());
		final Integer[] imgheights = new Integer[images.size()];
		final Integer[] imgwidths = new Integer[images.size()];
		final Integer[] imgpixels = new Integer[images.size()];
		final String[] imgalts  = new String[images.size()];
		int withalt = 0;
		int i = 0;
		LinkedHashSet<String> images_text_map = new LinkedHashSet<String>();
		/* Prepare flat solr field values */
		for (final ImageEntry ie: images) {
		    final MultiProtocolURL uri = ie.url();
		    inboundLinks.remove(uri);
		    outboundLinks.remove(uri);
		    imgheights[i] = ie.height();
		    imgwidths[i] = ie.width();
		    imgpixels[i] = ie.height() < 0 || ie.width() < 0 ? -1 : ie.height() * ie.width();
		    imgurls.add(uri.toNormalform(true));
		    imgalts[i] = ie.alt();
		    for (String it: CommonPattern.SPACE.split(uri.toTokens())) images_text_map.add(it);
		    if (ie.alt() != null && ie.alt().length() > 0) {
		        SentenceReader sr = new SentenceReader(ie.alt());
		        while (sr.hasNext()) images_text_map.add(sr.next().toString());
		        withalt++;
		    }
		    i++;
		}
		StringBuilder images_text = new StringBuilder(images_text_map.size() * 6 + 1);
		for (String s: images_text_map) images_text.append(s.trim()).append(' ');
		add(doc, WebMapping.imagescount_i, images.size());
		add(doc, WebMapping.images_sxt, imgurls);
		add(doc, WebMapping.images_alt_sxt, imgalts);
		add(doc, WebMapping.images_height_val, imgheights);
		add(doc, WebMapping.images_width_val, imgwidths);
		add(doc, WebMapping.images_pixel_val, imgpixels);
		add(doc, WebMapping.images_withalt_i, withalt);
		add(doc, WebMapping.images_text_t, images_text.toString().trim());
	}
    
    /**
     * this method compresses a list of protocol names to an indexed list.
     * To do this, all 'http' entries are removed and considered as default.
     * The remaining entries are indexed as follows: a list of <i>-<p> entries is produced, where
     * <i> is an index pointing to the original index of the protocol entry and <p> is the protocol entry itself.
     * The <i> entry is formatted as a 3-digit decimal number with leading zero digits.
     * @param protocol
     * @return a list of indexed protocol entries
     */
    public static List<String> protocolList2indexedList(final List<String> protocol) {
        List<String> a = new ArrayList<String>();
        String p;
        for (int i = 0; i < protocol.size(); i++) {
        	p = protocol.get(i);
            if (!p.equals("http")) {
                String c = Integer.toString(i);
                while (c.length() < 3) c = "0" + c;
                a.add(c + "-" + p);
            }
        }
        return a;
    }
    
    /**
     * Uncompress indexed iplist of protocol names to a list of specified dimension.
     * @param iplist indexed list typically produced by protocolList2indexedList
     * @param dimension size of target list
     * @return a list of protocol names
     */
    public static List<String> indexedList2protocolList(Collection<Object> iplist, int dimension) {
        List<String> a = new ArrayList<String>(dimension);
        for (int i = 0; i < dimension; i++) a.add("http");
        if (iplist == null) return a;
        for (Object ip : iplist) {
            // ip format is 001-https but can be 4 digits  1011-https
        	String indexedProtocol = ((String) ip); 
            int i = indexedProtocol.indexOf('-');
            /* Silently ignore badly formatted entry */
            if(i > 0 && indexedProtocol.length() > (i + 1)) {
            	a.set(Integer.parseInt(indexedProtocol.substring(0, i)), indexedProtocol.substring(i+1));
            }
        }
        return a;
    }
    
}
