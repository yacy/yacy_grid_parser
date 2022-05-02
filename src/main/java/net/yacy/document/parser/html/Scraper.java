/**
 *  ContentScraper
 *  Copyright 2004 by Michael Peter Christen, @0rb1t3r
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

package net.yacy.document.parser.html;

import java.awt.Dimension;
import java.io.CharArrayReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.storage.CaseInsensitiveMap;
import net.yacy.cora.storage.SizeLimitedMap;
import net.yacy.cora.storage.SizeLimitedSet;
import net.yacy.cora.util.NumberTools;
import net.yacy.document.SentenceReader;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.Evaluation.Element;
import net.yacy.document.parser.html.Tag.TagName;
import net.yacy.grid.tools.AnchorURL;
import net.yacy.grid.tools.CommonPattern;
import net.yacy.grid.tools.Logger;
import net.yacy.grid.tools.MultiProtocolURL;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.ISO639;


public class Scraper {

    public static final int MAX_DOCSIZE = 40 * 1024 * 1024;

    private final char degree = '\u00B0';
    private final char[] minuteCharsHTML = "&#039;".toCharArray();

    protected static final String EMPTY_STRING = new String();

    private static final Pattern LB = Pattern.compile("\n");

    // class variables: collectors for links
    private final List<AnchorURL> anchors;
    private final LinkedHashMap<MultiProtocolURL, String> rss, css;
    private final LinkedHashMap<AnchorURL, EmbedEntry> embeds; // urlhash/embed relation
    private final List<ImageEntry> images;
    private final Set<AnchorURL> script, frames, iframes;
    private final CaseInsensitiveMap<String> metas;
    private final Map<String, MultiProtocolURL> hreflang, navigation;
    private final LinkedHashSet<String> titles;
    private final List<String> articles;
    private final List<Date> startDates, endDates;
    //private String headline;
    private List<String>[] headlines;
    private final ClusteredScoreMap<String> bold, italic, underline;
    private final List<String> li, dt, dd;
    private final CharBuffer content;
    private double lon, lat;
    private AnchorURL canonical, publisher;
    private final int maxLinks;
    private final VocabularyScraper vocabularyScraper;
    private final int timezoneOffset;
    private int breadcrumbs;
    //private JsonLD ld;
    private JSONObject ld;
    private boolean googleoff;

    /** links to icons that belongs to the document (mapped by absolute URL)*/
    private final Map<MultiProtocolURL, IconEntry> icons;

    /**
     * The document root {@link MultiProtocolURL}
     */
    private MultiProtocolURL root;

    /**
     * evaluation scores: count appearance of specific attributes
     */
    private final Evaluation evaluationScores;

    /**
     * scrape a document
     * @param root the document root url
     * @param maxLinks the maximum number of links to scrape
     * @param vocabularyScraper handles maps from class names to vocabulary names and from documents to a map from vocabularies to terms
     * @param timezoneOffset local time zone offset
     */
    @SuppressWarnings("unchecked")
    public Scraper(final MultiProtocolURL root, final int maxLinks, final VocabularyScraper vocabularyScraper, final int timezoneOffset) {
        // the root value here will not be used to load the resource.
        // it is only the reference for relative links
        super();
        assert root != null;
        this.root = root;
        this.maxLinks = maxLinks;
        this.vocabularyScraper = vocabularyScraper;
        this.timezoneOffset = timezoneOffset;
        this.evaluationScores = new Evaluation();
        this.rss = new SizeLimitedMap<MultiProtocolURL, String>(maxLinks);
        this.css = new SizeLimitedMap<MultiProtocolURL, String>(maxLinks);
        this.anchors = new ArrayList<AnchorURL>();
        this.images = new ArrayList<ImageEntry>();
        this.icons = new HashMap<>();
        this.embeds = new SizeLimitedMap<AnchorURL, EmbedEntry>(maxLinks);
        this.frames = new SizeLimitedSet<AnchorURL>(maxLinks);
        this.iframes = new SizeLimitedSet<AnchorURL>(maxLinks);
        this.metas = new CaseInsensitiveMap<String>();
        this.hreflang = new SizeLimitedMap<String, MultiProtocolURL>(maxLinks);
        this.navigation = new SizeLimitedMap<String, MultiProtocolURL>(maxLinks);
        this.script = new SizeLimitedSet<AnchorURL>(maxLinks);
        this.titles = new LinkedHashSet<String>();
        this.articles = new ArrayList<String>();
        this.startDates = new ArrayList<>();
        this.endDates = new ArrayList<>();
        this.headlines = (List<String>[]) Array.newInstance(ArrayList.class, 6);
        for (int i = 0; i < this.headlines.length; i++) this.headlines[i] = new ArrayList<String>();
        this.bold = new ClusteredScoreMap<String>(false);
        this.italic = new ClusteredScoreMap<String>(false);
        this.underline = new ClusteredScoreMap<String>(false);
        this.li = new ArrayList<String>();
        this.dt = new ArrayList<String>();
        this.dd = new ArrayList<String>();
        this.content = new CharBuffer(MAX_DOCSIZE, 1024);
        this.lon = 0.0d;
        this.lat = 0.0d;
        this.evaluationScores.match(Element.url, root.toNormalform(true));
        this.canonical = null;
        this.publisher = null;
        this.breadcrumbs = 0;
        this.ld = null;
        this.googleoff = false; // if this is false, it means that we are outside of an googleoff event. If it is true, we are just between googleoff and googleon
    }

    public void setLd(final JSONObject ld) {
        this.ld = ld;
    }

    public JSONObject getLd() {
        return this.ld;
    }

    public void finish() {
        this.content.trimToSize();
    }

    public void scrapeText(final char[] newtext0, final String insideTag) {
        if (this.googleoff) {
            //System.out.println("SKIPPING TEXT:" + new String(newtext0));
            return; // skip this
        }
        // System.out.println("SCRAPE: " + UTF8.String(newtext));
        if (insideTag != null && (TagName.script.name().equals(insideTag) || TagName.style.name().equals(insideTag))) return;
        int p, pl, q, s = 0;
        final char[] newtext = CharacterCoding.html2unicode(new String(newtext0)).toCharArray();

        // match evaluation pattern
        this.evaluationScores.match(Element.text, newtext);

        // try to find location information in text
        // Opencaching:
        // <nobr>N 50o 05.453&#039;</nobr><nobr>E 008o 30.191&#039;</nobr>
        // N 52o 28.025 E 013o 20.299
        location: while (s < newtext.length) {
            pl = 1;
            p = CharBuffer.indexOf(newtext, s, this.degree);
            if (p < 0) {p = CharBuffer.indexOf(newtext, s, "&deg;".toCharArray()); if (p >= 0) pl = 5;}
            if (p < 0) break location;
            q = CharBuffer.indexOf(newtext, p + pl, this.minuteCharsHTML);
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, "'".toCharArray());
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, " E".toCharArray());
            if (q < 0) q = CharBuffer.indexOf(newtext, p + pl, " W".toCharArray());
            if (q < 0 && newtext.length - p == 7 + pl) q = newtext.length;
            if (q < 0) break location;
            int r = p;
            while (r-- > 1) {
                if (newtext[r] == ' ') {
                    r--;
                    if (newtext[r] == 'N') {
                        this.lat =  Double.parseDouble(new String(newtext, r + 2, p - r - 2)) +
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'S') {
                        this.lat = -Double.parseDouble(new String(newtext, r + 2, p - r - 2)) -
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lon != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'E') {
                        this.lon =  Double.parseDouble(new String(newtext, r + 2, p - r - 2)) +
                                    Double.parseDouble(new String(newtext, p + pl + 1, q - p - pl - 1)) / 60.0d;
                        if (this.lat != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    if (newtext[r] == 'W') {
                        this.lon = -Double.parseDouble(new String(newtext, r + 2, p - r - 2)) -
                                    Double.parseDouble(new String(newtext, p + 2, q - p - pl - 1)) / 60.0d;
                        if (this.lat != 0.0d) break location;
                        s = q + 6;
                        continue location;
                    }
                    break location;
                }
            }
            break location;
        }
        // find tags inside text
        String b = cleanLine(Tag.stripAllTags(newtext));
        if ((insideTag != null) && (!(insideTag.equals("a")))) {
            // texts inside tags sometimes have no punctuation at the line end
            // this is bad for the text semantics, because it is not possible for the
            // condenser to distinguish headlines from text beginnings.
            // to make it easier for the condenser, a dot ('.') is appended in case that
            // no punctuation is part of the newtext line
            if ((b.length() != 0) && (!(SentenceReader.punctuation(b.charAt(b.length() - 1))))) b = b + '.';
            //System.out.println("*** Appended dot: " + b.toString());
        }
        // find http links inside text
        s = 0;
        String u;
        while (s < b.length()) {
            p = find(b, dpssp, s);
            if (p == Integer.MAX_VALUE) break;
            s = Math.max(0, p - 5);
            p = find(b, protp, s);
            if (p == Integer.MAX_VALUE) break;
            q = b.indexOf(" ", p + 1);
            u = b.substring(p, q < 0 ? b.length() : q);
            if (u.endsWith(".")) u = u.substring(0, u.length() - 1); // remove the '.' that was appended above
            s = p + 6;
            try {
                this.addAnchor(new AnchorURL(u));
                continue;
            } catch (final MalformedURLException e) {}
        }
        // append string to content
        if (!b.isEmpty()) {
            this.content.append(b);
            this.content.appendSpace();
        }
    }

    private final static Pattern dpssp = Pattern.compile("://");
    private final static Pattern protp = Pattern.compile("smb://|ftp://|http://|https://");

    private static final int find(final String s, final Pattern m, final int start) {
        final Matcher mm = m.matcher(s.subSequence(start, s.length()));
        if (!mm.find()) return Integer.MAX_VALUE;
        final int p = mm.start() + start;
        //final int p = s.indexOf(m, start);
        return (p < 0) ? Integer.MAX_VALUE : p;
    }

    /**
     * @param relativePath relative path to this document base URL
     * @return the absolute URL (concatenation of this document root with the relative path) or null when malformed
     */
    private AnchorURL absolutePath(final String relativePath) {
        try {
            return AnchorURL.newAnchor(this.root, relativePath);
        } catch (final Exception e) {
            return null;
        }
    }

    public void checkOpts(final Tag tag) {
        //System.out.println("### " + tag.toString());
        // vocabulary classes
        final String classprop = tag.getProperty("class", EMPTY_STRING);
        if (this.vocabularyScraper != null) this.vocabularyScraper.check(this.root, classprop, tag.getContent());

        // itemprop (schema.org)
        final String itemprop = tag.getProperty("itemprop", tag.getProperty("property", null));
        if (itemprop != null) {

            // special content (legacy, pre-JSON-LD-parsing)
            String propval = tag.getProperty("content"); // value for <meta itemprop="" content=""> see https://html.spec.whatwg.org/multipage/microdata.html#values
            if (propval == null) propval = tag.getProperty("datetime"); // html5 + schema.org#itemprop example: <time itemprop="startDate" datetime="2016-01-26">today</time> while each prop is optional
            if (propval != null) {                                           // html5 example: <time datetime="2016-01-26">today</time> while each prop is optional
                // check <itemprop with value="" > (schema.org)
                switch (itemprop) {
                    // <meta> itemprops of main element with microdata <div itemprop="geo" itemscope itemtype="http://schema.org/GeoCoordinates">
                    case "latitude": // <meta itemprop="latitude" content="47.2649990" />
                        this.lat = Double.parseDouble(propval); // TODO: possibly overwrite existing value (multiple coordinates in document)
                        break;                                  // TODO: risk to mix up existing coordinate if longitude not given too
                    case "longitude": // <meta itemprop="longitude" content="11.3428720" />
                        this.lon = Double.parseDouble(propval); // TODO: possibly overwrite existing value (multiple coordinates in document)
                        break;                                  // TODO: risk to mix up existing coordinate if latitude not given too

                    case "startDate": // <meta itemprop="startDate" content="2016-04-21T20:00">
                        try {
                            // parse ISO 8601 date
                            final Date startDate = ISO8601Formatter.FORMATTER.parse(propval, this.timezoneOffset).getTime();
                            this.startDates.add(startDate);
                        } catch (final ParseException e) {}
                        break;
                    case "endDate":
                        try {
                            // parse ISO 8601 date
                            final Date endDate = ISO8601Formatter.FORMATTER.parse(propval, this.timezoneOffset).getTime();
                            this.endDates.add(endDate);
                        } catch (final ParseException e) {}
                        break;
                }
            }
        }
    }

    /**
     * Parses sizes icon link attribute. (see
     * http://www.w3.org/TR/html5/links.html#attr-link-sizes) Eventual
     * duplicates are removed.
     *
     * @param sizesAttr
     *            sizes attribute string, may be null
     * @return a set of sizes eventually empty.
     */
    public static Set<Dimension> parseSizes(final String sizesAttr) {
        final Set<Dimension> sizes = new HashSet<Dimension>();
        final Set<String> tokens = parseSpaceSeparatedTokens(sizesAttr);
        for (final String token : tokens) {
            /*
             * "any" keyword may be present, but doesn't have to produce a
             * dimension result
             */
            if (token != null) {
                final Matcher matcher = IconEntry.SIZE_PATTERN.matcher(token);
                if (matcher.matches()) {
                    /* With given pattern no NumberFormatException can occur */
                    sizes.add(new Dimension(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
                }
            }
        }
        return sizes;
    }

    /**
     * Parses a space separated tokens attribute value (see
     * http://www.w3.org/TR/html5/infrastructure.html#space-separated-tokens).
     * Eventual duplicates are removed.
     *
     * @param attr
     *            attribute string, may be null
     * @return a set of tokens eventually empty
     */
    public static Set<String> parseSpaceSeparatedTokens(final String attr) {
        final Set<String> tokens = new HashSet<>();
        /* Check attr string is not empty to avoid adding a single empty string
         * in result */
        if (attr != null && !attr.trim().isEmpty()) {
            final String[] items = attr.trim().split(CommonPattern.SPACES.pattern());
            Collections.addAll(tokens, items);
        }
        return tokens;
    }

    /**
     * Retain only icon relations (standard and non standard) from tokens .
     * @param relTokens relationship tokens (parsed from a rel attribute)
     * @return a Set of icon relations, eventually empty
     */
    private Set<String> retainIconRelations(final Collection<String> relTokens) {
        final HashSet<String> iconRels = new HashSet<>();
        for(final String token : relTokens) {
            if(IconLinkRelations.isIconRel(token)) {
                iconRels.add(token.toLowerCase(Locale.ENGLISH));
            }
        }
        return iconRels;
    }

    public void scrapeTag0(final Tag tag) {
        checkOpts(tag);
        if (tag.hasName("img")) {
            final String src = tag.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final MultiProtocolURL url = absolutePath(src);
                    if (url != null) {
                        // use to allow parse of "550px", with better performance as Numberformat.parse
                        final int width = NumberTools.parseIntDecSubstring(tag.getProperty("width", "-1")); // Integer.parseInt fails on "200px"
                        final int height = NumberTools.parseIntDecSubstring(tag.getProperty("height", "-1"));
                        final ImageEntry ie = new ImageEntry(url, tag.getProperty("alt", EMPTY_STRING), width, height, -1);
                        this.images.add(ie);
                    }
                }
            } catch (final NumberFormatException e) {}
            this.evaluationScores.match(Element.imgpath, src);
        } else if(tag.hasName("base")) {
            try {
                String href = tag.getProperty("href", EMPTY_STRING);
                href = CharacterCoding.html2unicode(href);

                AnchorURL url;
                if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                    this.root = new MultiProtocolURL(url.toString());
                }
            } catch (final MalformedURLException e) {}
        } else if (tag.hasName("frame")) {
            final AnchorURL src = absolutePath(tag.getProperty("src", EMPTY_STRING));
            if(src != null) {
                tag.setProperty("src", src.toNormalform(true));
                src.setAll(tag.getProperties());
                //this.addAnchor(src); // don't add the frame to the anchors because the webgraph should not contain such links (by definition)
                this.frames.add(src);
                this.evaluationScores.match(Element.framepath, src.toNormalform(true));
            }
        } else if (tag.hasName("body")) {
            final String classprop = tag.getProperty("class", EMPTY_STRING);
            this.evaluationScores.match(Element.bodyclass, classprop);
        } else if (tag.hasName("meta")) {
            final String content = tag.getProperty("content", EMPTY_STRING);
            String name = tag.getProperty("name", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
                if (name.toLowerCase().equals("generator")) {
                    this.evaluationScores.match(Element.metagenerator, content);
                }
            }
            name = tag.getProperty("http-equiv", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
            name = tag.getProperty("property", EMPTY_STRING);
            if (name.length() > 0) {
                this.metas.put(name.toLowerCase(), CharacterCoding.html2unicode(content));
            }
        } else if (tag.hasName("area")) {
            final String areatitle = cleanLine(tag.getProperty("title", EMPTY_STRING));
            //String alt   = tag.getProperty("alt",EMPTY_STRING);
            final String href  = tag.getProperty("href", EMPTY_STRING);
            if (href.length() > 0) {
                tag.setProperty("name", areatitle);
                final AnchorURL url = absolutePath(href);
                if (url != null) {
                    tag.setProperty("href", url.toNormalform(true));
                    url.setAll(tag.getProperties());
                    this.addAnchor(url);
                }
            }
        } else if (tag.hasName("link")) {
            final String href = tag.getProperty("href", EMPTY_STRING);
            final AnchorURL newLink = absolutePath(href);

            if (newLink != null) {
                tag.setProperty("href", newLink.toNormalform(true));
                final String rel = tag.getProperty("rel", EMPTY_STRING);
                /* Rel attribute is supposed to be a set of space-separated tokens */
                final Set<String> relTokens = parseSpaceSeparatedTokens(rel);

                final String linktitle = tag.getProperty("title", EMPTY_STRING);
                final String type = tag.getProperty("type", EMPTY_STRING);
                final String hreflang = tag.getProperty("hreflang", EMPTY_STRING);

                final Set<String> iconRels = retainIconRelations(relTokens);
                /* Distinguish icons from images. It will enable for example to later search only images and no icons */
                if (!iconRels.isEmpty()) {
                    final String sizesAttr = tag.getProperty("sizes", EMPTY_STRING);
                    final Set<Dimension> sizes = parseSizes(sizesAttr);
                    IconEntry icon = this.icons.get(newLink);
                    /* There is already an icon with same URL for this document :
                     * they may have different rel attribute or different sizes (multi sizes ico file) or this may be a duplicate */
                    if(icon != null) {
                        icon.getRel().addAll(iconRels);
                        icon.getSizes().addAll(sizes);
                    } else {
                        icon = new IconEntry(newLink, iconRels, sizes);
                        this.icons.put(newLink, icon);
                    }
                } else if (rel.equalsIgnoreCase("canonical")) {
                    tag.setProperty("name", this.titles.size() == 0 ? "" : this.titles.iterator().next());
                    newLink.setAll(tag.getProperties());
                    this.addAnchor(newLink);
                    this.canonical = newLink;
                } else if (rel.equalsIgnoreCase("publisher")) {
                    this.publisher = newLink;
                } else if (rel.equalsIgnoreCase("top") || rel.equalsIgnoreCase("up") || rel.equalsIgnoreCase("next") || rel.equalsIgnoreCase("prev") || rel.equalsIgnoreCase("first") || rel.equalsIgnoreCase("last")) {
                    this.navigation.put(rel, newLink);
                } else if (rel.equalsIgnoreCase("alternate") && type.equalsIgnoreCase("application/rss+xml")) {
                    this.rss.put(newLink, linktitle);
                } else if (rel.equalsIgnoreCase("alternate") && hreflang.length() > 0) {
                    this.hreflang.put(hreflang, newLink);
                } else if (rel.equalsIgnoreCase("stylesheet") && type.equalsIgnoreCase("text/css")) {
                    this.css.put(newLink, rel);
                    this.evaluationScores.match(Element.csspath, href);
                } else if (!rel.equalsIgnoreCase("stylesheet") && !rel.equalsIgnoreCase("alternate stylesheet")) {
                    tag.setProperty("name", linktitle);
                    newLink.setAll(tag.getProperties());
                    this.addAnchor(newLink);
                }
            }
        } else if (tag.hasName("embed") || tag.hasName("source")) { //html5 tag
            final String src = tag.getProperty("src", EMPTY_STRING);
            try {
                if (src.length() > 0) {
                    final AnchorURL url = absolutePath(src);
                    if (url != null) {
                        final int width = Integer.parseInt(tag.getProperty("width", "-1"));
                        final int height = Integer.parseInt(tag.getProperty("height", "-1"));
                        tag.setProperty("src", url.toNormalform(true));
                        final EmbedEntry ie = new EmbedEntry(url, width, height, tag.getProperty("type", EMPTY_STRING), tag.getProperty("pluginspage", EMPTY_STRING));
                        this.embeds.put(url, ie);
                        url.setAll(tag.getProperties());
                        // this.addAnchor(url); // don't add the embed to the anchors because the webgraph should not contain such links (by definition)
                    }
                }
            } catch (final NumberFormatException e) {}
        } else if(tag.hasName("param")) {
            final String name = tag.getProperty("name", EMPTY_STRING);
            if (name.equalsIgnoreCase("movie")) {
                final AnchorURL url = absolutePath(tag.getProperty("value", EMPTY_STRING));
                if (url != null) {
                    tag.setProperty("value", url.toNormalform(true));
                    url.setAll(tag.getProperties());
                    this.addAnchor(url);
                }
            }
        } else if (tag.hasName("iframe")) {
            final AnchorURL src = absolutePath(tag.getProperty("src", EMPTY_STRING));
            if (src != null) {
                tag.setProperty("src", src.toNormalform(true));
                src.setAll(tag.getProperties());
                //this.addAnchor(src); // don't add the iframe to the anchors because the webgraph should not contain such links (by definition)
                this.iframes.add(src);
                this.evaluationScores.match(Element.iframepath, src.toNormalform(true));
            }
        } else if (tag.hasName("html")) {
            final String lang = tag.getProperty("lang", EMPTY_STRING);
            if (!lang.isEmpty()) // fake a language meta to preserv detection from <html lang="xx" />
                this.metas.put("dc.language",lang.substring(0,2)); // fix found entries like "hu-hu"
        }
    }

    public void scrapeTag1(final Tag tag) {
        final String content_text = Tag.stripAllTags(tag.getContent());
        checkOpts(tag);
        // System.out.println("ScrapeTag1: tag.tagname=" + tag.tagname + ", opts=" + tag.opts.toString() + ", text=" + UTF8.String(text));
        if (tag.hasName("a") && tag.getContenLength() < 2048) {
            String href = tag.getProperty("href", EMPTY_STRING);
            href = CharacterCoding.html2unicode(href);
            AnchorURL url;
            if ((href.length() > 0) && ((url = absolutePath(href)) != null)) {
                if (followDenied()) {
                    String rel = tag.getProperty("rel", EMPTY_STRING);
                    if (rel.length() == 0) rel = "nofollow"; else if (rel.indexOf("nofollow") < 0) rel += ",nofollow";
                    tag.setProperty("rel", rel);
                }
                tag.setProperty("text", content_text); // strip any inline html in tag text like  "<a ...> <span>test</span> </a>"
                tag.setProperty("href", url.toNormalform(true)); // we must assign this because the url may have resolved backpaths and may not be absolute
                url.setAll(tag.getProperties());
                recursiveParse(url, tag.getContent());
                this.addAnchor(url);
            }
            this.evaluationScores.match(Element.apath, href);
        }
        final String h;
        if (tag.hasName("div")) {
            final String id = tag.getProperty("id", EMPTY_STRING);
            this.evaluationScores.match(Element.divid, id);
            final String itemtype = tag.getProperty("itemtype", EMPTY_STRING);
            if (itemtype.equals("http://data-vocabulary.org/Breadcrumb")) {
                this.breadcrumbs++;
            }
        } else if ((tag.hasName("h1")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[0].add(h);
        } else if((tag.hasName("h2")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[1].add(h);
        } else if ((tag.hasName("h3")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[2].add(h);
        } else if ((tag.hasName("h4")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[3].add(h);
        } else if ((tag.hasName("h5")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[4].add(h);
        } else if ((tag.hasName("h6")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.headlines[5].add(h);
        } else if ((tag.hasName("title")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            this.titles.add(h);
            this.evaluationScores.match(Element.title, h);
        } else if ((tag.hasName("b")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tag.hasName("strong")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tag.hasName("em")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.bold.inc(h);
        } else if ((tag.hasName("i")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.italic.inc(h);
        } else if ((tag.hasName("u")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.underline.inc(h);
        } else if ((tag.hasName("li")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.li.add(h);
        } else if ((tag.hasName("dt")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.dt.add(h);
        } else if ((tag.hasName("dd")) && (tag.getContenLength() < 1024)) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.dd.add(h);
        } else if (tag.hasName("script")) {
            final String src = tag.getProperty("src", EMPTY_STRING);
            if (src.length() > 0) {
                final AnchorURL absoluteSrc = absolutePath(src);
                if(absoluteSrc != null) {
                    this.script.add(absoluteSrc);
                }
                this.evaluationScores.match(Element.scriptpath, src);
            } else {
                this.evaluationScores.match(Element.scriptcode, LB.matcher(new String(tag.getContent())).replaceAll(" "));
            }
        } else if (tag.hasName("article")) {
            h = cleanLine(CharacterCoding.html2unicode(content_text));
            if (h.length() > 0) this.articles.add(h);
        } else if (tag.hasName(TagName.time.name())) { // html5 tag <time datetime="2016-12-23">Event</time>
            h = tag.getProperty("datetime"); // TODO: checkOpts() also parses datetime property if in combination with schema.org itemprop=startDate/endDate
            if (h != null) { // datetime property is optional
                try {
                    final Date startDate = ISO8601Formatter.FORMATTER.parse(h, this.timezoneOffset).getTime();
                    this.startDates.add(startDate);
                } catch (final ParseException ex) { }
            }
        }
    }

    /**
     * Add an anchor to the anchors list, and trigger any eventual listener
     * @param anchor anchor to add. Must not be null.
     */
    private void addAnchor(final AnchorURL anchor) {
        this.anchors.add(anchor);
    }

    public void scrapeComment(final char[] comment) {
        final String s = new String(comment);
        if (s.indexOf("googleoff:") >= 0) {
            this.googleoff = true;
        }
        if (s.indexOf("googleon:") >= 0) {
            this.googleoff = false;
        }
        //System.out.println("COMMENT:" + s);
        this.evaluationScores.match(Element.comment, LB.matcher(new String(comment)).replaceAll(" "));
    }

    private String recursiveParse(final AnchorURL linkurl, final char[] inlineHtml) {
        if (inlineHtml.length < 14) return cleanLine(CharacterCoding.html2unicode(Tag.stripAllTags(inlineHtml)));

        // start a new scraper to parse links inside this text
        // parsing the content
        final Scraper scraper = new Scraper(this.root, this.maxLinks, this.vocabularyScraper, this.timezoneOffset);
        final Tokenizer tokenizer = new Tokenizer(scraper);
        try {
            FileUtils.copy(new CharArrayReader(inlineHtml), tokenizer);
        } catch (final IOException e) {
            Logger.warn("", e);
            return cleanLine(CharacterCoding.html2unicode(Tag.stripAllTags(inlineHtml)));
        } finally {
            try {
                tokenizer.close();
            } catch (final IOException e) {
            }
        }
        for (final AnchorURL entry: scraper.getAnchors()) {
            this.addAnchor(entry);
        }
        final String line = cleanLine(CharacterCoding.html2unicode(Tag.stripAllTags(scraper.content.getChars())));
        final StringBuilder altakk = new StringBuilder();
        for (final ImageEntry ie: scraper.images) {
            if (linkurl != null) {
                if (ie.alt() != null) altakk.append(ie.alt().trim()).append(' ');
                linkurl.setImageURL(ie.url());
                final AnchorURL a = new AnchorURL(linkurl);
                a.setTextProperty(line);
                a.setImageAlt(ie.alt());
                a.setImageURL(ie.url());
                ie.setLinkurl(a);
            }
            // this image may have been added recently from the same location (as this is a recursive parse)
            // we want to keep only one of them, check if they are equal
            if (this.images.size() > 0 && this.images.get(this.images.size() - 1).url().equals(ie.url())) {
                this.images.remove(this.images.size() - 1);
            }
            this.images.add(ie);
        }
        if (linkurl != null) {
            linkurl.setImageAlt(altakk.toString().trim());
        }

        scraper.close();
        return line;
    }

    public List<String> getTitles() {

        // some documents have a title tag as meta tag
        final String s = this.metas.get("title");
        if (s != null && s.length() > 0) {
            this.titles.add(s);
        }

        if (this.titles.size() == 0) {
            // take any headline
            for (int i = 0; i < this.headlines.length; i++) {
                if (!this.headlines[i].isEmpty()) {
                    this.titles.add(this.headlines[i].get(0));
                    break;
                }
            }
        }

        // extract headline from file name
        final ArrayList<String> t = new ArrayList<String>();
        t.addAll(this.titles);
        return t;
    }

    public String[] getHeadlines(final int i) {
        assert ((i >= 1) && (i <= this.headlines.length));
        return this.headlines[i - 1].toArray(new String[this.headlines[i - 1].size()]);
    }

    public String[] getBold() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.bold.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getBoldCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.bold.get(a[i]);
        return counter;
    }

    public String[] getItalic() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.italic.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getItalicCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.italic.get(a[i]);
        return counter;
    }

    public String[] getUnderline() {
        final List<String> a = new ArrayList<String>();
        final Iterator<String> i = this.underline.keys(false);
        while (i.hasNext()) a.add(i.next());
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getUnderlineCount(final String[] a) {
        final Integer[] counter = new Integer[a.length];
        for (int i = 0; i < a.length; i++) counter[i] = this.underline.get(a[i]);
        return counter;
    }

    public String[] getLi() {
        return this.li.toArray(new String[this.li.size()]);
    }

    public String[] getDt() {
        return this.dt.toArray(new String[this.dt.size()]);
    }

    public String[] getDd() {
        return this.dd.toArray(new String[this.dd.size()]);
    }

    public List<Date> getStartDates() {
        return this.startDates;
    }

    public List<Date> getEndDates() {
        return this.endDates;
    }

    public MultiProtocolURL[] getFlash() {
        String ext;
        final ArrayList<MultiProtocolURL> f = new ArrayList<MultiProtocolURL>();
        for (final MultiProtocolURL url: this.anchors) {
            ext = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext == null) continue;
            if (ext.equals("swf")) f.add(url);
        }
        return f.toArray(new MultiProtocolURL[f.size()]);
    }

    public boolean containsFlash() {
        String ext;
        for (final MultiProtocolURL url: this.anchors) {
            ext = MultiProtocolURL.getFileExtension(url.getFileName());
            if (ext == null) continue;
            if (ext.equals("swf")) return true;
        }
        return false;
    }

    public int breadcrumbCount() {
        return this.breadcrumbs;
    }

    public String getText() {
        try {
            return this.content.trim().toString();
        } catch (final OutOfMemoryError e) {
            Logger.warn("", e);
            return "";
        }
    }

    public List<String> getArticles() {
        return this.articles;
    }

    public List<AnchorURL> getAnchors() {
        // returns a url (String) / name (String) relation
        return this.anchors;
    }

    public LinkedHashMap<MultiProtocolURL, String> getRSS() {
        // returns a url (String) / name (String) relation
        return this.rss;
    }

    public Map<MultiProtocolURL, String> getCSS() {
        // returns a url (String) / name (String) relation
        return this.css;
    }

    public Set<AnchorURL> getFrames() {
        // returns a url (String) / name (String) relation
        return this.frames;
    }

    public Set<AnchorURL> getIFrames() {
        // returns a url (String) / name (String) relation
        return this.iframes;
    }

    public Set<AnchorURL> getScript() {
        return this.script;
    }

    public AnchorURL getCanonical() {
        return this.canonical;
    }

    public MultiProtocolURL getPublisherLink() {
        return this.publisher;
    }

    public Map<String, MultiProtocolURL> getHreflang() {
        return this.hreflang;
    }

    public Map<String, MultiProtocolURL> getNavigation() {
        return this.navigation;
    }

    /**
     * get all images
     * @return a map of <urlhash, ImageEntry>
     */
    public List<ImageEntry> getImages() {
        return this.images;
    }

    public Map<AnchorURL, EmbedEntry> getEmbeds() {
        return this.embeds;
    }

    public Map<String, String> getMetas() {
        return this.metas;
    }

    /**
     * @return all icons links
     */
    public Map<MultiProtocolURL, IconEntry> getIcons() {
        return this.icons;
    }

    /*
    DC in html example:
    <meta name="DC.title" lang="en" content="Expressing Dublin Core in HTML/XHTML meta and link elements" />
    <meta name="DC.creator" content="Andy Powell, UKOLN, University of Bath" />
    <meta name="DC.identifier" scheme="DCTERMS.URI" content="http://dublincore.org/documents/dcq-html/" />
    <meta name="DC.format" scheme="DCTERMS.IMT" content="text/html" />
    <meta name="DC.type" scheme="DCTERMS.DCMIType" content="Text" />
    */

    public boolean indexingDenied() {
        final String s = this.metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("noindex",0) >= 0) return true;
        return false;
    }

    public boolean followDenied() {
        final String s = this.metas.get("robots");
        if (s == null) return false;
        if (s.indexOf("nofollow",0) >= 0) return true;
        return false;
    }

    public List<String> getDescriptions() {
        String s = this.metas.get("description");
        if (s == null) s = this.metas.get("dc.description");
        final List<String> descriptions = new ArrayList<String>();
        if (s == null) return descriptions;
        descriptions.add(s);
        return descriptions;
    }

    public String getContentType() {
        final String s = this.metas.get("content-type");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    public String getAuthor() {
        String s = this.metas.get("author");
        if (s == null) s = this.metas.get("dc.creator");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    public String getPublisher() {
        String s = this.metas.get("copyright");
        if (s == null) s = this.metas.get("dc.publisher");
        if (s == null) return EMPTY_STRING;
        return s;
    }

    private final static Pattern commaSepPattern = Pattern.compile(" |,");
    private final static Pattern semicSepPattern = Pattern.compile(" |;");

    public Set<String> getContentLanguages() {
        // i.e. <meta name="DC.language" content="en" scheme="DCTERMS.RFC3066">
        // or <meta http-equiv="content-language" content="en">
        String s = this.metas.get("content-language");
        if (s == null) s = this.metas.get("dc.language");
        if (s == null) return null;
        final Set<String> hs = new HashSet<String>();
        final String[] cl = commaSepPattern.split(s);
        int p;
        for (int i = 0; i < cl.length; i++) {
            cl[i] = cl[i].toLowerCase();
            p = cl[i].indexOf('-');
            if (p > 0) cl[i] = cl[i].substring(0, p);
            if (ISO639.exists(cl[i])) hs.add(cl[i]);
        }
        if (hs.isEmpty()) return null;
        return hs;
    }

    public String[] getKeywords() {
        String s = this.metas.get("keywords");
        if (s == null) s = this.metas.get("dc.description");
        if (s == null) s = EMPTY_STRING;
        if (s.isEmpty()) {
            return new String[0];
        }
        if (s.contains(",")) return commaSepPattern.split(s);
        if (s.contains(";")) return semicSepPattern.split(s);
        return s.split("\\s");
    }

    public int getRefreshSeconds() {
        final String s = this.metas.get("refresh");
        if (s == null) return 9999;
        try {
            final int pos = s.indexOf(';');
            if (pos < 0) return 9999;
            final int i = NumberTools.parseIntDecSubstring(s, 0, pos);
            return i;
        } catch (final NumberFormatException e) {
            return 9999;
        }
    }

    public String getRefreshPath() {
        String s = this.metas.get("refresh");
        if (s == null) return EMPTY_STRING;

        final int pos = s.indexOf(';');
        if (pos < 0) return EMPTY_STRING;
        s = s.substring(pos + 1).trim();
        if (s.toLowerCase().startsWith("url=")) return s.substring(4).trim();
        return EMPTY_STRING;
    }

    public Date getDate() {
        return getDate(this.metas, this.timezoneOffset);
    }

    public static Date getDate(final CaseInsensitiveMap<String> metas, final int timezoneOffset) {
        String content;

        // <meta name="date" content="YYYY-MM-DD..." />
        content = metas.get("date");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        // <meta name="DC.date.modified" content="YYYY-MM-DD" />
        content = metas.get("dc.date.modified");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        // <meta name="DC.date.created" content="YYYY-MM-DD" />
        content = metas.get("dc.date.created");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        // <meta name="DC.date" content="YYYY-MM-DD" />
        content = metas.get("dc.date");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        // <meta name="DC:date" content="YYYY-MM-DD" />
        content = metas.get("dc:date");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        // <meta http-equiv="last-modified" content="YYYY-MM-DD" />
        content = metas.get("last-modified");
        if (content != null) try {return getDate(content, timezoneOffset);} catch (final ParseException e) {}

        return new Date();
    }

    public static Date getDate(final String content, final int timezoneOffset) throws ParseException {
        // for some strange reason some dates are submitted as seconds or milliseconds; try to parse that
        try {
            final long d = Long.parseLong(content);
            try {
            	// try the number as seconds
                final Date s = verifyDate(new Date(1000L * d));
                return s;
            } catch (final ParseException ee) {
            	// try the number as milliseconds
            	return verifyDate(new Date(d));
            }
        } catch (final NumberFormatException e) {
            // we should expect that!
        	// try the string as ISO 860
            return verifyDate(ISO8601Formatter.FORMATTER.parse(content, timezoneOffset).getTime());
        }
    }

    public static Date verifyDate(final Date date) throws ParseException {
        final long millis = date.getTime();
        if (millis <= 0) throw new ParseException("date is negative", 0);
        final Date now = new Date();
        if (date.after(now)) throw new ParseException("date is future", 0);
        return date;
    }

    // parse location
    // <meta NAME="ICBM" CONTENT="38.90551492, 1.454004505" />
    // <meta NAME="geo.position" CONTENT="38.90551492;1.454004505" />

    public double getLon() {
        if (this.lon != 0.0d) return this.lon;
        String s = this.metas.get("ICBM"); // InterContinental Ballistic Missile (abbrev. supposed to be a joke: http://www.jargon.net/jargonfile/i/ICBMaddress.html), see http://geourl.org/add.html#icbm
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Double.parseDouble(s.substring(0, p).trim());
                this.lon = Double.parseDouble(s.substring(p + 1).trim());
            }
        }
        if (this.lon != 0.0d) return this.lon;
        s = this.metas.get("geo.position"); // http://geotags.com/geobot/add-tags.html
        if (s != null) {
            int p = s.indexOf(';');
            if (p < 0) p = s.indexOf(',');
            if (p < 0) p = s.indexOf(' ');
            if (p > 0) {
                this.lat = Double.parseDouble(s.substring(0, p).trim());
                this.lon = Double.parseDouble(s.substring(p + 1).trim());
            }
        }
        return this.lon;
    }

    public double getLat() {
        if (this.lat != 0.0d) return this.lat;
        getLon(); // parse with getLon() method which creates also the lat value
        return this.lat;
    }

    /**
     * produce all model names
     * @return a set of model names
     */
    public Set<String> getEvaluationModelNames() {
        return this.evaluationScores.getModelNames();
    }

    public String[] getEvaluationModelScoreNames(final String modelName) {
        final List<String> a = new ArrayList<String>();
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        if (scores != null) {
            final Iterator<String> i = scores.keys(false);
            while (i.hasNext()) a.add(i.next());
        }
        return a.toArray(new String[a.size()]);
    }

    public Integer[] getEvaluationModelScoreCounts(final String modelName, final String[] a) {
        final ClusteredScoreMap<String> scores = this.evaluationScores.getScores(modelName);
        final Integer[] counter = new Integer[a.length];
        if (scores != null) {
            for (int i = 0; i < a.length; i++) counter[i] = scores.get(a[i]);
        }
        return counter;
    }

    public void close() {
        // free resources
        this.anchors.clear();
        this.rss.clear();
        this.css.clear();
        this.script.clear();
        this.frames.clear();
        this.iframes.clear();
        this.embeds.clear();
        this.images.clear();
        this.icons.clear();
        this.metas.clear();
        this.hreflang.clear();
        this.navigation.clear();
        this.titles.clear();
        this.articles.clear();
        this.startDates.clear();
        this.endDates.clear();
        this.headlines = null;
        this.bold.clear();
        this.italic.clear();
        this.underline.clear();
        this.li.clear();
        this.dt.clear();
        this.dd.clear();
        this.content.clear();
        this.root = null;
    }

    public void print() {
        for (final String t: this.titles) {
            System.out.println("TITLE    :" + t);
        }
        for (int i = 0; i < 4; i++) {
            System.out.println("HEADLINE" + i + ":" + this.headlines[i].toString());
        }
        System.out.println("ANCHORS  :" + this.anchors.toString());
        System.out.println("IMAGES   :" + this.images.toString());
        System.out.println("METAS    :" + this.metas.toString());
        System.out.println("TEXT     :" + this.content.toString());
    }


    protected final static String cleanLine(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        char l = ' ';
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (c < ' ') c = ' ';
            if (c == ' ') {
                if (l != ' ') sb.append(c);
            } else {
                sb.append(c);
            }
            l = c;
        }

        // return result
        return sb.toString().trim();
    }

    public static void main(final String[] args) {
        // test getDate()
        final CaseInsensitiveMap<String> testmeta = new CaseInsensitiveMap<>();
        testmeta.put("Last-Modified","1648426480");
        final Date date = getDate(testmeta, 0);
        System.out.println(date.toString());
    }

}

