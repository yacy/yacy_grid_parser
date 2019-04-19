// Response.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.crawler.retrieval;

import java.util.Date;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.grid.tools.Classification;
import net.yacy.grid.tools.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
public class Response {

    // doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // the class objects
    private final  Request            request;
    private final  RequestHeader      requestHeader;
    private final  ResponseHeader     responseHeader;
    private        byte[]             content;
    private        int                status;          // tracker indexing status, see status defs below
    private final  boolean            fromCache;
    
    /** Maximum file size to put in cache for crawler */
    public static final long CRAWLER_MAX_SIZE_TO_CACHE = 10 * 1024L * 1024L;

    /**
     * doctype calculation by file extension
     * TODO: this must be enhanced with a more generic way of configuration
     * @param ext
     * @return a character denoting the file type
     */
    public static char docTypeExt(final String ext) {
        if (ext == null) return DT_UNKNOWN;
        if (ext.equals("gif"))  return DT_IMAGE;
        if (ext.equals("ico"))  return DT_IMAGE;
        if (ext.equals("bmp"))  return DT_IMAGE;
        if (ext.equals("jpg"))  return DT_IMAGE;
        if (ext.equals("jpeg")) return DT_IMAGE;
        if (ext.equals("png"))  return DT_IMAGE;
        if (ext.equals("tif"))  return DT_IMAGE;
        if (ext.equals("tiff")) return DT_IMAGE;
        if (ext.equals("htm"))  return DT_HTML;
        if (ext.equals("html")) return DT_HTML;
        if (ext.equals("txt"))  return DT_TEXT;
        if (ext.equals("doc"))  return DT_DOC;
        if (ext.equals("rtf"))  return DT_DOC;
        if (ext.equals("pdf"))  return DT_PDFPS;
        if (ext.equals("ps"))   return DT_PDFPS;
        if (ext.equals("mp3"))  return DT_AUDIO;
        if (ext.equals("aac"))  return DT_AUDIO;
        if (ext.equals("m4a"))  return DT_AUDIO;
        if (ext.equals("ogg"))  return DT_AUDIO;
        if (ext.equals("wav"))  return DT_AUDIO;
        if (ext.equals("wma"))  return DT_AUDIO;
        if (ext.equals("avi"))  return DT_MOVIE;
        if (ext.equals("mov"))  return DT_MOVIE;
        if (ext.equals("qt"))   return DT_MOVIE;
        if (ext.equals("mpg"))  return DT_MOVIE;
        if (ext.equals("mp4"))  return DT_MOVIE;
        if (ext.equals("m4v"))  return DT_MOVIE;
        if (ext.equals("mkv"))  return DT_MOVIE;
        if (ext.equals("md5"))  return DT_SHARE;
        if (ext.equals("mpeg")) return DT_MOVIE;
        if (ext.equals("asf"))  return DT_FLASH;
        return DT_UNKNOWN;
    }
    
    /**
     * doctype calculation based on file extensions; this is the url wrapper
     * @param url
     * @return a character denoting the file type
     */
    public static char docType(final MultiProtocolURL url) {
        String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        if (ext == null) return DT_UNKNOWN;
        return docTypeExt(ext);
    }

    /**
     * doctype calculation based on the mime type
     * @param mime
     * @return a character denoting the file type
     */
    public static char docType(final String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        return doctype;
    }

    /**
     * reverse mime type calculation; this is just a heuristic
     * @param ext
     * @param doctype
     * @return a mime type string
     */
    public static String[] doctype2mime(String ext, char doctype) {
        if (doctype == DT_PDFPS) return new String[]{"application/pdf"};
        if (doctype == DT_HTML) return new String[]{"text/html"};
        if (doctype == DT_DOC) return new String[]{"application/msword"};
        if (doctype == DT_FLASH) return new String[]{"application/x-shockwave-flash"};
        if (doctype == DT_SHARE) return new String[]{"text/plain"};
        if (doctype == DT_BINARY) return new String[]{"application/octet-stream"};
        String mime = Classification.ext2mime(ext);
        int p = mime.indexOf('/');
        if (p < 0) return new String[]{mime};
        if (doctype == DT_TEXT) return new String[]{"text" + mime.substring(p)};
    	if (doctype == DT_IMAGE) return new String[]{"image" + mime.substring(p)};
    	if (doctype == DT_AUDIO) return new String[]{"audio" + mime.substring(p)};
    	if (doctype == DT_MOVIE) return new String[]{"video" + mime.substring(p)};
    	return new String[]{mime};
    }

    public static final int QUEUE_STATE_FRESH             = 0;
    public static final int QUEUE_STATE_PARSING           = 1;
    public static final int QUEUE_STATE_CONDENSING        = 2;
    public static final int QUEUE_STATE_STRUCTUREANALYSIS = 3;
    public static final int QUEUE_STATE_INDEXSTORAGE      = 4;
    public static final int QUEUE_STATE_FINISHED          = 5;

    public Response(
            final Request request,
            final RequestHeader requestHeader,
            final ResponseHeader responseHeader,
            final boolean fromCache,
            final byte[] content) {
        this.request = request;
        // request and response headers may be zero in case that we process surrogates
        this.requestHeader = requestHeader;
        this.responseHeader = responseHeader;
        this.status = QUEUE_STATE_FRESH;
        this.content = content;
        this.fromCache = fromCache;
        if (this.responseHeader != null && content != null && Integer.parseInt(this.responseHeader.get(HeaderFramework.CONTENT_LENGTH, "0")) <= content.length) {
            this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(content.length)); // repair length 
        }
    }

    /**
     * create a 'virtual' response that is composed using crawl details from the request object
     * this is used when the NOLOAD queue is processed
     * @param request
     * @param profile
     */
    public Response(final Request request) {
        this.request = request;
        // request and response headers may be zero in case that we process surrogates
        this.requestHeader = null;
        this.responseHeader = new ResponseHeader(200);
        this.responseHeader.put(HeaderFramework.CONTENT_TYPE, Classification.ext2mime(MultiProtocolURL.getFileExtension(request.url().getFileName()), "text/plain")); // tell parser how to handle the content
        this.status = QUEUE_STATE_FRESH;
        this.content = request.name().length() > 0 ? UTF8.getBytes(request.name()) : UTF8.getBytes(request.url().toTokens());
        this.fromCache = true;
        if (this.responseHeader != null) this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, "0"); // 'virtual' length, shows that the resource was not loaded
    }
    
    public void updateStatus(final int newStatus) {
        this.status = newStatus;
    }

    public RequestHeader getRequestHeader() {
        return this.requestHeader;
    }

    public ResponseHeader getResponseHeader() {
        return this.responseHeader;
    }

    public boolean fromCache() {
        return this.fromCache;
    }

    public int getStatus() {
        return this.status;
    }

    public String name() {
        // the anchor name; can be either the text inside the anchor tag or the
        // page description after loading of the page
        return this.request.name();
    }

    public MultiProtocolURL url() {
        return this.request.url();
    }

    public char docType() {
        char doctype = docType(getMimeType());
        if (doctype == DT_UNKNOWN) doctype = docType(url());
        return doctype;
    }

    /**
     * Get respons header last modified date
     * if missing the first seen date or current date
     * @return valid date always != null
     */
    public Date lastModified() {
        Date docDate = null;

        if (this.responseHeader != null) {
            docDate = this.responseHeader.lastModified(); // is always != null
        }
        if (docDate == null && this.request != null) docDate = this.request.appdate();
        if (docDate == null) docDate = new Date();

        return docDate;
    }


    public byte[] initiator() {
        return this.request.initiator();
    }

    public boolean proxy() {
        return initiator() == null;
    }

    public long size() {
        if (this.responseHeader != null && this.responseHeader.getContentLengthLong() != -1) {
            // take the size from the response header
            return this.responseHeader.getContentLengthLong();
        }
        if (this.content != null) return this.content.length;
        // the size is unknown
        return -1;
    }

    public int depth() {
        return this.request.depth();
    }

    public void setContent(final byte[] data) {
        this.content = data;
        if (this.responseHeader != null && this.content != null && Integer.parseInt(this.responseHeader.get(HeaderFramework.CONTENT_LENGTH, "0")) <= content.length) {
            this.responseHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(content.length)); // repair length 
        }
    }

    public byte[] getContent() {
        return this.content;
    }

    /**
     * Get Mime type from http header or null if unknown (not included in response header)
     * @return mime (trimmed and lowercase) or null
     */
    public String getMimeType() {
        if (this.responseHeader == null) return null;

        String mimeType = this.responseHeader.getContentType();
        if (mimeType != null) {
            mimeType = mimeType.trim().toLowerCase();

            final int pos = mimeType.indexOf(';');
            return ((pos < 0) ? mimeType : mimeType.substring(0, pos));
        }
        return null;
    }

    public String getCharacterEncoding() {
        if (this.responseHeader == null) return null;
        return this.responseHeader.getCharacterEncoding();
    }

    public MultiProtocolURL referrerURL() {
        if (this.requestHeader == null) return null;
        return this.requestHeader.referer();
    }

    public boolean validResponseStatus() {
        int status = this.responseHeader.getStatusCode();
        return status == 200 || status == 203;
    }

    public Date ifModifiedSince() {
        return (this.requestHeader == null) ? null : this.requestHeader.ifModifiedSince();
    }

    public boolean requestWithCookie() {
        return (this.requestHeader == null) ? false : this.requestHeader.containsKey(RequestHeader.COOKIE);
    }

    public boolean requestProhibitsIndexing() {
        return (this.requestHeader == null)
        ? false
        : this.requestHeader.containsKey(HeaderFramework.X_YACY_INDEX_CONTROL) &&
          (this.requestHeader.get(HeaderFramework.X_YACY_INDEX_CONTROL)).toUpperCase().equals("NO-INDEX");
    }

}
