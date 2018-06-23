/**
 *  Tag
 *  Copyright 2018 by Michael Peter Christen, @0rb1t3r
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

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import ai.susi.json.JsonLDNode;
import net.yacy.kelondro.io.CharBuffer;

public class Tag {
    
    private final static int MAX_TAGSIZE = 1024 * 1024;

    public static final char sp = ' ';
    public static final char lb = '<';
    public static final char rb = '>';
    public static final char sl = '/';
    
    private static final Set<String> linkTags0 = new HashSet<String>(12,0.99f);
    private static final Set<String> linkTags1 = new HashSet<String>(15,0.99f);
    
    private String name;
    private Properties opts;
    private JsonLDNode ld;
    private CharBuffer content;
    
    static {
        for (final TagName tag: TagName.values()) {
            if (tag.type == TagType.singleton) linkTags0.add(tag.name());
            if (tag.type == TagType.pair) linkTags1.add(tag.name());
        }
    }

    public static boolean isTag0(final String tag) {
        return (linkTags0 != null) && (linkTags0.contains(tag.toLowerCase()));
    }

    public static boolean isTag1(final String tag) {
        return (linkTags1 != null) && (linkTags1.contains(tag.toLowerCase()));
    }
    
    public enum TagType {
        singleton, pair;
    }

    public enum TagName {
        a(TagType.pair),
        abbr(TagType.pair),
        acronym(TagType.pair),
        address(TagType.pair),
        applet(TagType.pair),
        area(TagType.singleton),
        article(TagType.pair),
        aside(TagType.pair),
        audio(TagType.pair),
        b(TagType.pair),
        base(TagType.singleton),
        basefont(TagType.singleton),
        bdi(TagType.pair),
        bdo(TagType.pair),
        big(TagType.pair),
        blockquote(TagType.pair),
        body(TagType.pair),
        br(TagType.singleton),
        button(TagType.pair),
        canvas(TagType.pair),
        caption(TagType.pair),
        center(TagType.pair),
        cite(TagType.pair),
        code(TagType.pair),
        col(TagType.singleton),
        colgroup(TagType.pair),
        data(TagType.pair),
        datalist(TagType.pair),
        dd(TagType.pair),
        del(TagType.pair),
        details(TagType.pair),
        dfn(TagType.pair),
        dialog(TagType.pair),
        dir(TagType.pair),
        div(TagType.pair),
        dl(TagType.pair),
        dt(TagType.pair),
        em(TagType.pair),
        embed(TagType.singleton), //added by [MN]
        fieldset(TagType.pair),
        figcaption(TagType.pair),
        figure(TagType.pair),
        font(TagType.pair),
        footer(TagType.pair),
        form(TagType.pair),
        frame(TagType.singleton),
        frameset(TagType.pair),
        h1(TagType.pair),
        h2(TagType.pair),
        h3(TagType.pair),
        h4(TagType.pair),
        h5(TagType.pair),
        h6(TagType.pair),
        head(TagType.pair),
        header(TagType.pair),
        hr(TagType.singleton),
        html(TagType.singleton), // scraped as singleton to get attached properties like 'lang'
        i(TagType.pair),
        iframe(TagType.singleton), // scraped as singleton to get such iframes that have no closing tag
        img(TagType.singleton),
        input(TagType.singleton),
        ins(TagType.pair),
        li(TagType.pair),
        link(TagType.singleton),
        meta(TagType.singleton),
        p(TagType.pair),
        param(TagType.singleton), //added by [MN]
        script(TagType.pair),
        small(TagType.pair),
        source(TagType.singleton), // html5 (part of <video> <audio>) - scaped like embed
        span(TagType.pair),
        strong(TagType.pair),
        style(TagType.pair), // embedded css (if not declared as tag content is parsed as text)
        time(TagType.pair), // html5 <time datetime>
        title(TagType.pair),
        u(TagType.pair);

        public TagType type;
        private TagName(final TagType type) {
            this.type = type;
        }
    }

    
    public Tag(final String name, final Properties opts) {
        this.name = name;
        this.opts = opts;
        this.ld = new JsonLDNode();
        this.content = new CharBuffer(MAX_TAGSIZE);
    }
    
    public void close() {
        this.name = null;
        this.opts = null;
        if (this.content != null) this.content.close();
        this.content = null;
    }
    
    public String getName() {
        return this.name;
    }
    
    public Properties getProperties() {
        return this.opts;
    }
    
    public boolean hasName(String name) {
        return this.name.equalsIgnoreCase(name);
    }
    
    public boolean hasProperty(String key) {
        return this.opts.containsKey(key);
    }
    
    public String getProperty(String key) {
        return this.opts.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return this.opts.getProperty(key, defaultValue);
    }
    
    public void setProperty(String key, String value) {
        this.opts.setProperty(key, value);
    }
    
    public JsonLDNode ld() {
        return this.ld;
    }
    
    public char[] getContent() {
        return this.content.getChars();
    }
    
    public int getContenLength() {
        return this.content.length();
    }
    
    public void appendToContent(char[] chars) {
        this.content.append(chars);
    }
    
    public void addFlatToParent(Tag peer) {
        peer.learnLdFromProperties();
        this.ld.putAll(peer.ld);
    }
    
    public void addChildToParent(Tag child) {
        child.learnLdFromProperties();
        // We call this method to give a hint that the new node data comes from a sub-structure of the html
        // It may happen that we have sub-structures but still no child relationships. The child relationship is only here
        // if the parent has the "itemprop" or "property" property
        String itemprop = this.opts.getProperty("itemprop", this.opts.getProperty("property", null));
        if (itemprop == null) {
            // not a child but we still must collect double entries and collect them into arrays
            for (String key: child.ld.keySet()) {
                if (this.ld.has(key)) {
                    // prevent overwriting by creation or extension of an array
                    Object o0 = this.ld.get(key);
                    Object o1 = child.ld.get(key);
                    if (o0 instanceof JSONArray) {
                        ((JSONArray) o0).put(o1);
                    } else {
                        JSONArray a = new JSONArray();
                        a.put(o0);
                        a.put(o1);
                        this.ld.put(key, a);
                    }
                } else {
                    this.ld.put(key, child.ld.get(key));
                }
            }
        } else {
            // a child
            // check if the item is already set because then the new node must be appended to existing data
            if (this.ld.has(itemprop) && this.ld.get(itemprop) instanceof JSONObject) {
                this.ld.getJSONObject(itemprop).putAll(child.ld);
            } else {
                this.ld.put(itemprop, child.ld);
            }
        }
    }
    
    public char[] genOpts(final char quotechar) {
        if (this.opts.isEmpty()) return null;
        final Enumeration<?> e = this.opts.propertyNames();
        final CharBuffer bb = new CharBuffer(Scraper.MAX_DOCSIZE, this.opts.size() * 40);
        String key;
        while (e.hasMoreElements()) {
            key = (String) e.nextElement();
            bb.appendSpace().append(key).append('=').append(quotechar);
            bb.append(this.opts.getProperty(key));
            bb.append(quotechar);
        }
        final char[] result;
        if (bb.length() > 0)
            result = bb.getChars(1);
        else
            result = bb.getChars();
        bb.close();
        return result;
    }
    
    public void learnLdFromProperties() {

        String itemtype = this.opts.getProperty("itemtype", null); // microdata
        if (itemtype != null) {
            this.ld.addContext(null, itemtype);
            return;
        }
        
        String vocab = this.opts.getProperty("vocab", null); // RDFa
        if (vocab != null) {
            this.ld.addContext(null, vocab);
        }
        String typeof = this.opts.getProperty("typeof", null);
        if (typeof != null) {
            this.ld.addType(typeof);
        }
        
        // itemprop (schema.org)
        String itemprop = this.opts.getProperty("itemprop", this.opts.getProperty("property", null));
        if (itemprop != null) {
            // set content text but do not overwrite properties to prevent that we overwrite embedded objects
            String content_text = null;
            if (this.opts.containsKey("content")) {
                // For RDFa and microdata the content property key is the same!
                content_text = this.opts.getProperty("content");
            } else {
                // If no content is given within the tag properties, either the content of the tag or an embedded json-ld node is used.
                // Embedded json-ld nodes are handled with the addChildToParent method. This here is for leaf objects.
                content_text = stripAllTags(this.getContent());
            }
            if (content_text != null) {
                if (!this.ld.has(itemprop) || (this.ld.get(itemprop) instanceof JSONObject && this.ld.getJSONObject(itemprop).length() == 0)) {
                    this.ld.setPredicate(itemprop, content_text);
                }
            }
        }
    }
        
    @Override
    public void finalize() {
        this.close();
    }
    @Override
    public String toString() {
        String s = "html:\n" + new String(toChars('"'));
        s += "\nld:\n" + this.ld.toString(2);
        return s;
    }
    
    public char[] toChars(final char quotechar) {
        char[] text = this.getContent();
        boolean singleton = text.length == 0;
        final char[] gt0 = this.tagHead(singleton, quotechar);
        if (singleton) return gt0;
        
        final CharBuffer cb = new CharBuffer(Scraper.MAX_DOCSIZE, gt0, gt0.length + text.length + this.getName().length() + 3);
        cb.append(text).append('<').append('/').append(this.getName()).append('>');
        final char[] result = cb.getChars();
        cb.close();
        return result;
    }

    public static char[] toChars(final String tagname, final boolean opening, final char[] tagopts) {
        final CharBuffer bb = new CharBuffer(Scraper.MAX_DOCSIZE, tagname.length() + tagopts.length + 3);
        bb.append('<');
        if (!opening) {
            bb.append('/');
        }
        bb.append(tagname);
        if (tagopts.length > 0) {
            bb.append(tagopts);
        }
        bb.append('>');
        final char[] result = bb.getChars();
        bb.close();
        return result;
    }

    private char[] tagHead(boolean singleton, final char quotechar) {
        final char[] tagoptsx = this.genOpts(quotechar);
        final CharBuffer bb = new CharBuffer(Scraper.MAX_DOCSIZE, this.getName().length() + ((tagoptsx == null) ? 0 : (tagoptsx.length + 1)) + this.getName().length() + 3);
        bb.append('<').append(this.getName());
        if (tagoptsx != null) {
            bb.appendSpace();
            bb.append(tagoptsx);
        }
        if (singleton) bb.append('/');
        bb.append('>');
        final char[] result = bb.getChars();
        bb.close();
        return result;
    }
    
    public static String stripAllTags(final char[] s) {
        final StringBuilder r = new StringBuilder(s.length);
        int bc = 0;
        for (final char c : s) {
            if (c == lb) {
                bc++;
                if (r.length() > 0 && r.charAt(r.length() - 1) != sp) r.append(sp);
            } else if (c == rb) {
                bc--;
            } else if (bc <= 0) {
                r.append(c);
            }
        }
        return r.toString().trim();
    }
}
