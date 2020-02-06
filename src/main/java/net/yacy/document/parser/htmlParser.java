/**
 *  htmlParser.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.LinkedHashMap;

import org.apache.any23.Any23;
import org.apache.any23.extractor.ExtractionException;
import org.apache.any23.source.ByteArrayDocumentSource;
import org.apache.any23.writer.JSONLDWriter;
import org.apache.any23.writer.TripleHandlerException;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.grid.http.ClientConnection;
import net.yacy.grid.tools.CommonPattern;
import net.yacy.grid.tools.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.Scraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.document.parser.html.Tokenizer;
import net.yacy.kelondro.util.FileUtils;

import com.ibm.icu.text.CharsetDetector;

public class htmlParser extends AbstractParser implements Parser {

    private static final int maxLinks = 10000;

    public htmlParser() {
        super("Streaming HTML Parser");
        this.SUPPORTED_EXTENSIONS.add("htm");
        this.SUPPORTED_EXTENSIONS.add("html");
        this.SUPPORTED_EXTENSIONS.add("shtml");
        this.SUPPORTED_EXTENSIONS.add("shtm");
        this.SUPPORTED_EXTENSIONS.add("stm");
        this.SUPPORTED_EXTENSIONS.add("xhtml");
        this.SUPPORTED_EXTENSIONS.add("phtml");
        this.SUPPORTED_EXTENSIONS.add("phtm");
        this.SUPPORTED_EXTENSIONS.add("tpl");
        this.SUPPORTED_EXTENSIONS.add("php");
        this.SUPPORTED_EXTENSIONS.add("php2");
        this.SUPPORTED_EXTENSIONS.add("php3");
        this.SUPPORTED_EXTENSIONS.add("php4");
        this.SUPPORTED_EXTENSIONS.add("php5");
        this.SUPPORTED_EXTENSIONS.add("cfm");
        this.SUPPORTED_EXTENSIONS.add("asp");
        this.SUPPORTED_EXTENSIONS.add("aspx");
        this.SUPPORTED_EXTENSIONS.add("tex");
        this.SUPPORTED_EXTENSIONS.add("txt");
        this.SUPPORTED_EXTENSIONS.add("msg");

        this.SUPPORTED_MIME_TYPES.add("text/html");
        this.SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
        this.SUPPORTED_MIME_TYPES.add("application/xhtml+xml");
        this.SUPPORTED_MIME_TYPES.add("application/x-httpd-php");
        this.SUPPORTED_MIME_TYPES.add("application/x-tex");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-outlook");
        this.SUPPORTED_MIME_TYPES.add("text/plain");
        this.SUPPORTED_MIME_TYPES.add("text/csv");
    }

    @Override
    public Document[] parse(
            final MultiProtocolURL location,
            final String mimeType,
            final String documentCharset,
            final VocabularyScraper vocscraper,
            final int timezoneOffset,
            final InputStream sourceStream) throws Parser.Failure, InterruptedException {

        try {
            // first get a document from the parsed html
            Charset[] detectedcharsetcontainer = new Charset[]{null};
            Scraper scraper = parseToScraper(location, documentCharset, vocscraper, detectedcharsetcontainer, timezoneOffset, sourceStream, maxLinks);
            // parseToScraper also detects/corrects/sets charset from html content tag
            final Document document = transformScraper(location, mimeType, detectedcharsetcontainer[0].name(), scraper);
            return new Document[]{document};
        } catch (final IOException e) {
            throw new Parser.Failure("IOException in htmlParser: " + e.getMessage(), location);
        }
    }

    /**
     *  the transformScraper method transforms a scraper object into a document object
     * @param location
     * @param mimeType
     * @param charSet
     * @param scraper
     * @return a Document instance
     */
    private Document transformScraper(final MultiProtocolURL location, final String mimeType, final String charSet, final Scraper scraper) {
        final String[] sections = new String[
                 scraper.getHeadlines(1).length +
                 scraper.getHeadlines(2).length +
                 scraper.getHeadlines(3).length +
                 scraper.getHeadlines(4).length +
                 scraper.getHeadlines(5).length +
                 scraper.getHeadlines(6).length];
        int p = 0;
        for (int i = 1; i <= 6; i++) {
            for (final String headline : scraper.getHeadlines(i)) {
                sections[p++] = headline;
            }
        }
        LinkedHashMap<MultiProtocolURL, ImageEntry> noDoubleImages = new LinkedHashMap<>();
        for (ImageEntry ie: scraper.getImages()) noDoubleImages.put(ie.url(), ie);
        final Document ppd = new Document(
                location,
                mimeType,
                charSet,
                this,
                scraper.getContentLanguages(),
                scraper.getKeywords(),
                scraper.getTitles(),
                scraper.getAuthor(),
                scraper.getPublisher(),
                sections,
                scraper.getDescriptions(),
                scraper.getLon(), scraper.getLat(),
                scraper.getText(),
                scraper.getAnchors(),
                scraper.getRSS(),
                noDoubleImages,
                scraper.indexingDenied(),
                scraper.getDate());
        ppd.setScraperObject(scraper);
        ppd.setIcons(scraper.getIcons());
        ppd.ld().putAll(scraper.getLd().getJSON());
        return ppd;
    }

    public static Scraper parseToScraper(final MultiProtocolURL location, final String documentCharset, final VocabularyScraper vocabularyScraper, final int timezoneOffset, final String input, final int maxLinks) throws IOException {
        Charset[] detectedcharsetcontainer = new Charset[]{null};
        InputStream sourceStream;
        try {
            sourceStream = new ByteArrayInputStream(documentCharset == null ? UTF8.getBytes(input) : input.getBytes(documentCharset));
        } catch (UnsupportedEncodingException e) {
            sourceStream = new ByteArrayInputStream(UTF8.getBytes(input));
        }
        Scraper scraper; // for this static methode no need to init local this.scraperObject
        try {
            scraper = parseToScraper(location, documentCharset, vocabularyScraper, detectedcharsetcontainer, timezoneOffset, sourceStream, maxLinks);
        } catch (Failure e) {
            throw new IOException(e.getMessage());
        }
        return scraper;
    }
    
    public static Scraper parseToScraper(
            final MultiProtocolURL location,
            final String documentCharset,
            final VocabularyScraper vocabularyScraper,
            Charset[] detectedcharsetcontainer,
            final int timezoneOffset,
            InputStream sourceStream,
            final int maxLinks) throws Parser.Failure, IOException {

        // make a scraper
        String charset = null;

        // ah, we are lucky, we got a character-encoding via HTTP-header
        if (documentCharset != null) {
            charset = patchCharsetEncoding(documentCharset);
        }

        // the author didn't tell us the encoding, try the mozilla-heuristic
        if (charset == null) {
            final CharsetDetector det = new CharsetDetector();
            det.enableInputFilter(true);
            final InputStream detStream = new BufferedInputStream(sourceStream);
            det.setText(detStream);
            charset = det.detect().getName();
            sourceStream = detStream;
        }

        // wtf? still nothing, just take system-standard
        if (charset == null) {
            detectedcharsetcontainer[0] = Charset.defaultCharset();
        } else {
            try {
                detectedcharsetcontainer[0] = Charset.forName(charset);
            } catch (final IllegalCharsetNameException e) {
                detectedcharsetcontainer[0] = Charset.defaultCharset();
            } catch (final UnsupportedCharsetException e) {
                detectedcharsetcontainer[0] = Charset.defaultCharset();
            }
        }
        
        // parsing the content
        // for this static methode no need to init local this.scraperObject here
        final Scraper scraper = new Scraper(location, maxLinks, vocabularyScraper, timezoneOffset);
        final Tokenizer tokenizer = new Tokenizer(scraper);
        try {
            FileUtils.copy(sourceStream, tokenizer, detectedcharsetcontainer[0]);
        } catch (final IOException e) {
            throw new Parser.Failure("IO error:" + e.getMessage(), location, e);
        } finally {
            tokenizer.flush();
            //sourceStream.close(); keep open for multiple parsing (close done by caller)
            tokenizer.close();
        }
        //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);
        //serverFileUtils.copy(sourceFile, hfos);
        //hfos.close();
        if (tokenizer.binarySuspect()) {
            final String errorMsg = "Binary data found in resource";
            throw new Parser.Failure(errorMsg, location);
        }
        scraper.setLd(tokenizer.ld());
        return scraper;
    }

    /**
     * some html authors use wrong encoding names, either because they don't know exactly what they
     * are doing or they produce a type. Many times, the upper/downcase scheme of the name is fuzzy
     * This method patches wrong encoding names. The correct names are taken from
     * http://www.iana.org/assignments/character-sets
     * @param encoding
     * @return patched encoding name
     */
    public static String patchCharsetEncoding(String encoding) {

        // do nothing with null
        if ((encoding == null) || (encoding.length() < 3)) return null;

        // trim encoding string
        encoding = encoding.trim();

        // fix upper/lowercase
        encoding = encoding.toUpperCase();
        if (encoding.startsWith("SHIFT")) return "Shift_JIS";
        if (encoding.startsWith("BIG")) return "Big5";
        // all other names but such with "windows" use uppercase
        if (encoding.startsWith("WINDOWS")) encoding = "windows" + encoding.substring(7);
        if (encoding.startsWith("MACINTOSH")) encoding = "MacRoman";

        // fix wrong fill characters
        encoding = CommonPattern.UNDERSCORE.matcher(encoding).replaceAll("-");

        if (encoding.matches("GB[_-]?2312([-_]80)?")) return "GB2312";
        if (encoding.matches(".*UTF[-_]?8.*")) return StandardCharsets.UTF_8.name();
        if (encoding.startsWith("US")) return StandardCharsets.US_ASCII.name();
        if (encoding.startsWith("KOI")) return "KOI8-R";

        // patch missing '-'
        if (encoding.startsWith("windows") && encoding.length() > 7) {
            final char c = encoding.charAt(7);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(7);
            }
        }

        if (encoding.startsWith("ISO")) {
            // patch typos
            if (encoding.length() > 3) {
                final char c = encoding.charAt(3);
                if ((c >= '0') && (c <= '9')) {
                    encoding = "ISO-" + encoding.substring(3);
                }
            }
            if (encoding.length() > 8) {
                final char c = encoding.charAt(8);
                if ((c >= '0') && (c <= '9')) {
                    encoding = encoding.substring(0, 8) + "-" + encoding.substring(8);
                }
            }
        }

        // patch wrong name
        if (encoding.startsWith("ISO-8559")) {
            // popular typo
            encoding = "ISO-8859" + encoding.substring(8);
        }

        // converting cp\d{4} -> windows-\d{4}
        if (encoding.matches("CP([_-])?125[0-8]")) {
            final char c = encoding.charAt(2);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(2);
            } else {
                encoding = "windows" + encoding.substring(2);
            }
        }

        return encoding;
    }

    public static Document[] load(String url) throws IOException {
        byte[] b = ClientConnection.load(url);
        return parse(url, b);
    }
    
    public static Document[] parse(String url, byte[] b) throws IOException {
        MultiProtocolURL location = new MultiProtocolURL(url);
        htmlParser parser = new htmlParser();
        Document[] docs;
        try {
            docs = parser.parse(location, "text/html", "UTF-8", null, -60, new ByteArrayInputStream(b));
        } catch (Failure | InterruptedException e) {
            throw new IOException (e.getMessage());
        }
        return docs;
    }

    public static Document[] parse(String context) throws IOException {
        htmlParser parser = new htmlParser();
        MultiProtocolURL location = new MultiProtocolURL("http://context.local");
        Document[] docs;
        byte[] b = context.getBytes(StandardCharsets.UTF_8);
        try {
            docs = parser.parse(location, "text/html", "UTF-8", null, -60, new ByteArrayInputStream(b));
            return docs;
        } catch (Failure | InterruptedException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public static String JSONLDExpand2Mode(String url, String jsons, JSONLDMode mode) throws IOException {
        byte[] jsonb = jsons.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonb);
        Model model = Rio.parse(bais, url, RDFFormat.JSONLD);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFWriter writer = Rio.createWriter(RDFFormat.JSONLD, baos);
        writer.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, mode);
        writer.startRDF();
        for (Statement st: model) writer.handleStatement(st);
        writer.endRDF();
        baos.close();
        jsonb = baos.toByteArray();
        jsons = new String(jsonb, StandardCharsets.UTF_8);
        return jsons;
    }

    public static String RDFa2JSONLDExpandString(String url, byte[] b) throws IOException {
        Any23 any23 = new Any23();
        ByteArrayDocumentSource ds = new ByteArrayDocumentSource(b, url, "text/html"); // text/html; application/xhtml+xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JSONLDWriter th = new JSONLDWriter(baos);
        try {
            any23.extract(ds, th);
            th.close();
            baos.close();
        } catch (IOException | ExtractionException | TripleHandlerException e) {
            throw new IOException(e.getCause());
        }
        byte[] jsonb = baos.toByteArray();
        String jsons = new String(jsonb, StandardCharsets.UTF_8);
        return jsons;
    }

    public static void main(String[] args) {

        // verify RDFa with
        // https://www.w3.org/2012/pyRdfa/Overview.html#distill_by_input
        // http://rdf.greggkellogg.net/distiller?command=serialize&format=rdfa&output_format=jsonld
        // https://rdfa.info/play/
        // http://linter.structured-data.org/

        /*
        if (args.length == 2) {
            // parse from an etherpad
            String etherpad = args[0];
            String apikey = args[1];
            String[] pads = new String[] {"05cc1575f55de2dc82f20f9010d71358", "c8f2a54127f96b38a85623cb472e33cd"};
            for (String padid: pads) {
                try {
                    String content = ClientConnection.loadFromEtherpad(etherpad, apikey, padid);
                    Document[] docs = parse(content);
                    System.out.println(docs[0].dc_title());
                    System.out.println(docs[0].ld().toString(2));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        */

        String[] testurl = new String[] {
                //"https://www.foodnetwork.com/recipes/tyler-florence/chicken-marsala-recipe-1951778",
                //"https://www.amazon.de/Hitchhikers-Guide-Galaxy-Paperback-Douglas/dp/B0043WOFQG",
                //"https://developers.google.com/search/docs/guides/intro-structured-data",
                //"https://www.bbcgoodfood.com/recipes/9652/bestever-tiramisu",
                //"https://www.livegigs.de/konzert/madball/duesseldorf-stone-im-ratinger-hof/2018-06-19",
                //"https://www.mags.nrw/arbeit",
                "https://release-8-0-x-dev-224m2by-lj6ob4e22x2mc.eu.platform.sh/test", // unvollständig
                "http://fim-landesredaktion.nrw/ultimateRdfa.html", // unvollständig
                "https://files.gitter.im/yacy/publicplan/OJR0/error1.html", // 2. Anschrift und kommunikation fehlt
                "https://files.gitter.im/yacy/publicplan/eol2/error2.html", // OK!
                "https://files.gitter.im/yacy/publicplan/41gy/error2-wirdSoIndexiert.html" // 2. Kommunikation fehlt
        };
        for (String url: testurl) {
            try {
                byte[] b = ClientConnection.load(url);
                String s = RDFa2JSONLDExpandString(url, b);
                JSONArray jaExpand = new JSONArray(s);
                JSONArray jaFlatten = new JSONArray(JSONLDExpand2Mode(url, s, JSONLDMode.FLATTEN));
                JSONObject jaCompact = new JSONObject(JSONLDExpand2Mode(url, s, JSONLDMode.COMPACT));
                Document[] docs = parse(url, b);
                System.out.println("URL     : " + url);
                System.out.println("Title   : " + docs[0].dc_title());
                System.out.println("Content : " + docs[0].getTextString());
                System.out.println("JSON-LD : " + docs[0].ld().toString(2));
                System.out.println("any23-e : " + jaExpand.toString(2));
                System.out.println("any23-f : " + jaFlatten.toString(2));
                System.out.println("any23-c : " + jaCompact.toString(2));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }
}
