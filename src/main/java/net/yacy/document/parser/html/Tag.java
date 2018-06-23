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

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import net.yacy.kelondro.io.CharBuffer;

public class Tag {
    
    private final static int MAX_TAGSIZE = 1024 * 1024;
    
    private static final Set<String> linkTags0 = new HashSet<String>(12,0.99f);
    private static final Set<String> linkTags1 = new HashSet<String>(15,0.99f);
    
    private String name;
    private Properties opts;
    private CharBuffer content;
    private int depth = 0;
    
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
        body(TagType.singleton), // scraped as singleton to get attached properties like 'class'
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

    
    public Tag(final String name) {
        this.name = name;
        this.opts = new Properties();
        this.content = new CharBuffer(MAX_TAGSIZE);
    }
    public Tag(final String name, final Properties opts) {
        this.name = name;
        this.opts = opts;
        this.content = new CharBuffer(MAX_TAGSIZE);
    }
    public Tag(final String name, final Properties opts, final CharBuffer content) {
        this.name = name;
        this.opts = opts;
        this.content = content;
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
    public char[] getContent() {
        return this.content.getChars();
    }
    public int getContenLength() {
        return this.content.length();
    }
    public void appendToContent(char[] chars) {
        this.content.append(chars);
    }
    public void setDepth(int d) {
        this.depth = d;
    }
    public int getDepth() {
        return this.depth;
    }
    @Override
    public void finalize() {
        this.close();
    }
    @Override
    public String toString() {
        return "<" + name + " " + opts + ">" + content + "</" + name + "> [" + this.depth + "]";
    }
}
