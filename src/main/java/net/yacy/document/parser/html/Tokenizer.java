// htmlFilterOutputStream.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

/*
 This class implements an output stream. Any data written to that output
 is automatically parsed.
 After finishing with writing, the htmlFilter can be read out.

 */

package net.yacy.document.parser.html;

import java.io.IOException;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Stack;

import net.yacy.document.parser.html.Tag.TagName;
import net.yacy.kelondro.io.CharBuffer;


public final class Tokenizer extends Writer {

	private static final char lb = '<';
	private static final char rb = '>';
	private static final char dash = '-';
	private static final char excl = '!';
	private static final char singlequote = '\'';
	private static final char doublequote = '"';

    private CharBuffer buffer;
    private Stack<Tag> tagStack;
    private final Scraper scraper;
    private boolean inSingleQuote;
    private boolean inDoubleQuote;
    private boolean inComment;
    private boolean binaryUnsuspect;
    
    public Tokenizer(final Scraper scraper) {
        this.scraper       = scraper;
        this.buffer        = new CharBuffer(Scraper.MAX_DOCSIZE, 64);
        this.tagStack      = new Stack<Tag>();
        this.inSingleQuote = false;
        this.inDoubleQuote = false;
        this.inComment     = false;
        this.binaryUnsuspect = true;
    }

    /*
     * Implementation of the writer
     */

    /**
     * this is the tokenizer of the parser: it splits the input into pieces which are
     * - quoted text parts
     * - commented text parts
     * - tags (opening and closing)
     * - text content between all these parts
     * The tokens are then parsed with the tokenProcessor method
     */
    @Override
    public void write(final int c) throws IOException {
        //System.out.println((char) c);
        if ((this.binaryUnsuspect) && (binaryHint((char)c))) {
            this.binaryUnsuspect = false;
        }

        if (this.inSingleQuote) {
            this.buffer.append(c);
            if (c == singlequote) this.inSingleQuote = false;
            // check error cases
            if ((c == rb) && (this.buffer.length() > 0 && this.buffer.charAt(0) == lb)) {
                this.inSingleQuote = false;
                // the tag ends here. after filtering: pass on
                tokenProcessor(this.buffer.getChars(), singlequote);
                // this.buffer = new serverByteBuffer();
                this.buffer.reset();
            }
        } else if (this.inDoubleQuote) {
            this.buffer.append(c);
            if (c == doublequote) this.inDoubleQuote = false;
            // check error cases
            if (c == rb && this.buffer.length() > 0 && this.buffer.charAt(0) == lb) {
                this.inDoubleQuote = false;
                // the tag ends here. after filtering: pass on
                tokenProcessor(this.buffer.getChars(), doublequote);
                // this.buffer = new serverByteBuffer();
                this.buffer.reset();
            }
        } else if (this.inComment) {
            this.buffer.append(c);
            if (c == rb &&
                this.buffer.length() > 6 &&
                this.buffer.charAt(this.buffer.length() - 3) == dash) {
                // comment is at end
                this.inComment = false;
                final char[] comment = this.buffer.getChars();
                this.scraper.scrapeComment(comment);
                // this.buffer = new serverByteBuffer();
                this.buffer.reset();
            }
        } else {
            if (this.buffer.isEmpty()) {
                if (c != rb) {
                    this.buffer.append(c);
                }
            } else if (this.buffer.length() > 0 && this.buffer.charAt(0) == lb) {
                if (c == singlequote) this.inSingleQuote = true;
                if (c == doublequote) this.inDoubleQuote = true;
                // fill in tag text
                if ((this.buffer.length() >= 3) && (this.buffer.charAt(1) == excl) &&
                    (this.buffer.charAt(2) == dash) && (c == dash)) {
                    // this is the start of a comment
                    this.inComment = true;
                    this.buffer.append(c);
                } else if (c == rb) {
                    this.buffer.append(c);
                    // the tag ends here. after filtering: pass on
                    tokenProcessor(this.buffer.getChars(), doublequote);
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                } else if (c == lb) {
                    // this is an error case
                    // we consider that there is one rb missing
                    if (this.buffer.length() > 0) {
                        tokenProcessor(this.buffer.getChars(), doublequote);
                    }
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                    this.buffer.append(c);
                } else {
                    this.buffer.append(c);
                }
            } else {
                // fill in plain text
                if (c == lb) {
                    // the text ends here
                    if (this.buffer.length() > 0) {
                        tokenProcessor(this.buffer.getChars(), doublequote);
                    }
                    // this.buffer = new serverByteBuffer();
                    this.buffer.reset();
                    this.buffer.append(c);
                } else {
                    // simply append
                    this.buffer.append(c);
                }
            }
        }
    }

    @Override
    public void write(final char b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final char b[], final int off, final int len) throws IOException {
//      System.out.println(UTF8.String(b, off, len));
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) throw new IndexOutOfBoundsException();
        for (int i = off ; i < (len - off) ; i++) this.write(b[i]);
    }

    @Override
    public void flush() throws IOException {
        this.scraper.finish();
        // if you want to flush all, call close() at end of writing;
    }

    @Override
    public void close() throws IOException {
        flush();
        final char quotechar = (this.inSingleQuote) ? singlequote : doublequote;
        if (this.buffer != null) {
            if (this.buffer.length() > 0) tokenProcessor(this.buffer.getChars(), quotechar);
            this.buffer.close();
            this.buffer = null;
        }
        processFinalize(quotechar);
        this.tagStack.clear();
        this.tagStack = null;
        this.scraper.finish();
    }

    private static boolean binaryHint(final char c) {
        // space, punctiation and symbols, letters and digits (ASCII/latin)
        //if (c >= 31 && c < 128) return false;
        if(c > 31) return false;
        //  8 = backspace
        //  9 = horizontal tab
        // 10 = new line (line feed)
        // 11 = vertical tab
        // 12 = new page (form feed)
        // 13 = carriage return
        if (c > 7 && c <= 13) return false;
        return true;
    }

    public boolean binarySuspect() {
        return !this.binaryUnsuspect;
    }
    
    
    
    /*
     * Transformer
     */
    
    /**
     * the token processor distinguishes three different types of input: opening tag, closing tag, text content
     * @param in - the token to be processed
     * @param quotechar
     * @return a processed version of the token
     */
    private void tokenProcessor(final char[] in, final char quotechar) {
        if (in.length == 0) return;
        
        // scan the string and parse structure
        if (in.length <= 2 || in[0] != lb) {
            processTag(in); // this is a text
            return;
        }

        // this is a tag
        String tag;
        int tagend;
        if (in[1] == '/') {
            // a closing tag
            tagend = findTagEnd(in, 2);
            tag = new String(in, 2, tagend - 2).toLowerCase();
            final char[] text = new char[in.length - tagend - 1];
            System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
            processTag(text, quotechar, tag, false);
            return;
        }

        // don't add text from within <script> section, here e.g. a "if 1<a" expression could confuse tag detection
        if (this.tagStack.size()>0 && this.tagStack.lastElement().hasName(TagName.script.name())) {
            return;
        }

        // an opening tag
        tagend = findTagEnd(in, 1);
        tag = new String(in, 1, tagend - 1).toLowerCase();
        final char[] text = new char[in.length - tagend - 1];
        System.arraycopy(in, tagend, text, 0, in.length - tagend - 1);
        processTag(text, quotechar, tag, true);
    }

    private static int findTagEnd(final char[] tag, final int start) {
        char c;
        for (int i = start; i < tag.length; i++) {
            c = tag[i];
            if (c != '!' && c != '-' &&
                (c < '0' || c > '9') &&
                (c < 'a' || c > 'z') &&
                (c < 'A' || c > 'Z')
            ) return i;
        }
        return tag.length - 1;
    }

    // distinguish the following cases:
    // - (1) not collecting data for a tag and getting no tag (not opener and not close)
    // - (2) not collecting data for a tag and getting a tag opener
    // - (3) not collecting data for a tag and getting a tag close
    // - (4) collecting data for a tag and getting no tag (not opener and not close)
    // - (5) collecting data for a tag and getting a new/different tag opener without closing the previous tag
    // - (6) collecting data for a tag and getting a tag close for the wrong tag (a different than the opener)
    // - (7) collecting data for a tag and getting the correct close tag for that collecting tag
    
    /**
     * 
     * @param content
     * @return content or empty array
     */
    private void processTag(final char[] content) {
        if (this.tagStack.size() == 0) {
            // we are not collection tag text -> case (1) - (3)
            // case (1): this is not a tag opener/closer
            if (content.length > 0) this.scraper.scrapeText(content, null);
            return;
        }

        // we are collection tag text for the tag 'filterTag' -> case (4) - (7)
        // case (4): getting no tag, go on collecting content
        this.scraper.scrapeText(content, this.tagStack.lastElement().getName());
        
        this.tagStack.lastElement().appendToContent(content);
    }
            
    private void processTag(final char[] content, final char quotechar, final String tagname, final boolean opening) {
        assert tagname != null;
        
        if (this.tagStack.size() == 0) {
            // we are not collection tag text -> case (1) - (3)

            // we have a new tag
            if (opening) {
                // case (2):
                processTagOpening(tagname, content, quotechar);
                return;
            }

            // its a close tag where no should be
            // case (3): we ignore that thing and return it again
            genTag0raw(tagname, false, content);
            return;

        }

        // we are collection tag text for the tag 'filterTag' -> case (4) - (7)
        if (tagname.equals("!")) processTag(content);

        // it's a tag! which one?
        if (opening) {
            // case (5): the opening should not be here. But we keep the order anyway
            this.tagStack.lastElement().appendToContent(processTagOpening(tagname, content, quotechar));
            return;
        }

        if (!tagname.equalsIgnoreCase(this.tagStack.lastElement().getName())) {
            // case (6): its a closing tag, but the wrong one. just add it.
            this.tagStack.lastElement().appendToContent(genTag0raw(tagname, opening, content));
            return;
        }

        // it's our closing tag! process complete result.
        processTagCloseing(quotechar);
    }

    private char[] processTagOpening(final String tagname, final char[] content, final char quotechar) {
        final CharBuffer charBuffer = new CharBuffer(Scraper.MAX_DOCSIZE, content);
        Tag tag = new Tag(tagname, charBuffer.propParser());
        tag.setDepth(this.tagStack.size());
        charBuffer.close();
        if (Tag.isTag0(tagname)) {
            // this single tag is collected at once here
            this.scraper.scrapeTag0(tag);
        }
        if (Tag.isTag1(tagname)) {
            // ok, start collecting; we don't push this here to the scraper or transformer; we do that when the tag is closed.
            this.tagStack.push(tag);
            return new char[0];
        } else {
             // we ignore that thing and return it again
            return genTag0raw(tagname, true, content);
        }
    }

    private void processTagCloseing(final char quotechar) {
        char[] ret;
        Tag tag = this.tagStack.lastElement();
        ret = genTag1(tag.getName(), tag.getProperties(), tag.getContent(), quotechar);
        if (Tag.isTag1(tag.getName())) {
            this.scraper.scrapeTag1(tag);
            // remove the tag from the stack as soon as the tag is processed
            this.tagStack.pop();
            // at this point the characters from the recently processed tag must be attached to the previous tag
            if (this.tagStack.size() > 0) this.tagStack.lastElement().appendToContent(ret);
        }
        if (!Tag.isTag1(tag.getName())) this.scraper.checkOpts(tag, new String(tag.getContent()));
    }

    private void processFinalize(final char quotechar) {
        if (this.tagStack.size() == 0) return;

        // it's our closing tag! return complete result.
        this.scraper.scrapeTag1(this.tagStack.lastElement());
        genTag1(this.tagStack.lastElement().getName(), this.tagStack.lastElement().getProperties(), this.tagStack.lastElement().getContent(), quotechar);
        this.tagStack.pop();
    }
    

    private static char[] genTag1(final String tagname, final Properties tagopts, final char[] text, final char quotechar) {
            final char[] gt0 = genTag0(tagname, tagopts, quotechar);
            final CharBuffer cb = new CharBuffer(Scraper.MAX_DOCSIZE, gt0, gt0.length + text.length + tagname.length() + 3);
            cb.append(text).append('<').append('/').append(tagname).append('>');
            final char[] result = cb.getChars();
            cb.close();
            return result;
    }

    
    private static char[] genTag0raw(final String tagname, final boolean opening, final char[] tagopts) {
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

    private static char[] genTag0(final String tagname, final Properties tagopts, final char quotechar) {
            final char[] tagoptsx = (tagopts.isEmpty()) ? null : genOpts(tagopts, quotechar);
            final CharBuffer bb = new CharBuffer(Scraper.MAX_DOCSIZE, tagname.length() + ((tagoptsx == null) ? 0 : (tagoptsx.length + 1)) + tagname.length() + 2);
            bb.append('<').append(tagname);
            if (tagoptsx != null) {
                bb.appendSpace();
                bb.append(tagoptsx);
            }
            bb.append('>');
            final char[] result = bb.getChars();
            bb.close();
            return result;
    }
    
    // a helper method for pretty-printing of properties for html tags
    private static char[] genOpts(final Properties prop, final char quotechar) {
            final Enumeration<?> e = prop.propertyNames();
            final CharBuffer bb = new CharBuffer(Scraper.MAX_DOCSIZE, prop.size() * 40);
            String key;
            while (e.hasMoreElements()) {
                key = (String) e.nextElement();
                bb.appendSpace().append(key).append('=').append(quotechar);
                bb.append(prop.getProperty(key));
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

}