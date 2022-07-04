/**
 *  ParserListener
 *  Copyright 1.04.2017 by Michael Peter Christen, @0rb1t3r
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

package net.yacy.grid.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import org.json.JSONArray;
import org.json.JSONObject;

import ai.susi.mind.SusiAction;
import ai.susi.mind.SusiThought;
import net.yacy.document.parser.pdfParser;
import net.yacy.grid.YaCyServices;
import net.yacy.grid.io.assets.Asset;
import net.yacy.grid.io.index.CrawlerDocument;
import net.yacy.grid.io.index.CrawlerDocument.Status;
import net.yacy.grid.io.index.CrawlerMapping;
import net.yacy.grid.io.index.WebMapping;
import net.yacy.grid.mcp.AbstractBrokerListener;
import net.yacy.grid.mcp.BrokerListener;
import net.yacy.grid.mcp.Configuration;
import net.yacy.grid.mcp.Service;
import net.yacy.grid.parser.api.ParserService;
import net.yacy.grid.tools.CronBox.Telemetry;
import net.yacy.grid.tools.DateParser;
import net.yacy.grid.tools.Digest;
import net.yacy.grid.tools.JSONList;
import net.yacy.grid.tools.Logger;
import net.yacy.grid.tools.Memory;

/*
 * test this with
 * curl -X POST -F "message=@job.json" -F "serviceName=parser" -F "queueName=yacyparser" http://yacygrid.com:8100/yacy/grid/mcp/messages/send.json
{
"metadata": {
"process": "yacy_grid_parser",
"count": 1
},
"data": [{"collection": "test"}],
"actions": [{
"type": "parser",
"queue": "yacyparser",
"sourceasset": "test3/yacy.net.warc.gz",
"targetasset": "test3/yacy.net.text.jsonlist",
"targetgraph": "test3/yacy.net.graph.jsonlist",
"actions": [
  {
  "type": "indexer",
  "queue": "elasticsearch",
  "targetindex": "webindex",
  "targettype" : "common",
  "sourceasset": "test3/yacy.net.text.jsonlist"
  },
  {
    "type": "crawler",
    "queue": "webcrawler",
    "sourceasset": "test3/yacy.net.graph.jsonlist"
  }
]
}]
}
 */
public class ParserListener extends AbstractBrokerListener implements BrokerListener {

    public ParserListener(final Configuration config, final YaCyServices service) {
         super(config, service, Runtime.getRuntime().availableProcessors());
    }

    @Override
    public ActionResult processAction(final SusiAction action, final JSONArray data, final String processName, final int processNumber) {

        // check short memory status
        if (Memory.shortStatus()) {
            pdfParser.clean_up_idiotic_PDFParser_font_cache_which_eats_up_tons_of_megabytes();
        }

        final String sourceasset_path = action.getStringAttr("sourceasset");
        final String targetasset_path = action.getStringAttr("targetasset");
        final String targetgraph_path = action.getStringAttr("targetgraph");
        final boolean archivewarc = action.getBooleanAttr("archivewarc");
        final boolean archiveindex = action.getBooleanAttr("archiveindex");
        final boolean archivegraph = action.getBooleanAttr("archivegraph");
        if (targetasset_path == null || targetasset_path.length() == 0 ||
            sourceasset_path == null || sourceasset_path.length() == 0) return ActionResult.FAIL_IRREVERSIBLE;

        byte[] source = null;
        if (action.hasAsset(sourceasset_path)) {
            source = action.getBinaryAsset(sourceasset_path);
        }
        if (source == null) try {
            final Asset<byte[]> asset = super.config.gridStorage.load(sourceasset_path);
            source = asset.getPayload();
        } catch (final Throwable e) {
            Logger.warn("Parser.processAction", e);
            // if we do not get the payload from the storage, we look for attached data in the action
            Logger.warn("Parser.processAction could not load asset: " + sourceasset_path, e);
            return ActionResult.FAIL_IRREVERSIBLE;
        }
        try{
            InputStream sourceStream = null;
            sourceStream = new ByteArrayInputStream(source);
            if (sourceasset_path.endsWith(".gz")) try {
                sourceStream = new GZIPInputStream(sourceStream);
            } catch (final ZipException e) {
                // This may actually not be in gzip format in case that a http process unzipped it already.
                // In that case we simply ignore the exception and the sourcestream stays as it is
            }

            // compute parsed documents
            final String crawl_id = action.getStringAttr("id");
            final String user_id = action.getStringAttr("user_id");
            final JSONArray user_ids = action.getArrayAttr("user_ids");

            final JSONObject crawl = SusiThought.selectData(data, "id", crawl_id);
            final Map<String, Pattern> collections = WebMapping.collectionParser(crawl.optString("collection"));
            final JSONArray parsedDocuments = ParserService.indexWarcRecords(sourceStream, collections);

            // enrich the parsed documents with crawl_id and user_id context
            for (int i = 0; i < parsedDocuments.length(); i++) {
                final JSONObject json = parsedDocuments.getJSONObject(i);
                if (crawl_id != null && crawl_id.length() > 0) json.put(WebMapping.crawl_id_s.name(), crawl_id);
                if (user_id != null && user_id.length() > 0) json.put(WebMapping.user_id_s.name(), user_id);
                if (user_ids != null && user_ids.length() > 0) json.put(WebMapping.user_id_sxt.name(), user_ids);
            }

            // store the assets to the indexing queue
            final JSONList targetasset_object = new JSONList();
            final JSONList targetgraph_object = new JSONList();
            for (int i = 0; i < parsedDocuments.length(); i++) {
                final JSONObject docjson = parsedDocuments.getJSONObject(i);
                final String url = docjson.getString(WebMapping.url_s.name());

                // create elasticsearch index line
                final String urlid = Digest.encodeMD5Hex(url);
                final JSONObject bulkjson = new JSONObject().put("index", new JSONObject().put("_id", urlid));

                // omit documents which have a canonical tag and are not self-addressed canonical documents
                boolean is_canonical = true;
                final String canonical_url = docjson.optString(WebMapping.canonical_s.name());
                if (canonical_url.length() > 0 && !url.equals(canonical_url)) is_canonical = false;

                final JSONObject updater = new JSONObject();
                updater.put(CrawlerMapping.status_date_dt.getMapping().name(), DateParser.iso8601MillisFormat.format(new Date()));
                if (is_canonical) {
                    // write web index document for canonical documents
                    targetasset_object.add(bulkjson);
                    //docjson.put("_id", id);
                    targetasset_object.add(docjson);
                    // put success into crawler index
                    updater
                        .put(CrawlerMapping.status_s.getMapping().name(), Status.parsed.name())
                        .put(CrawlerMapping.comment_t.getMapping().name(), docjson.optString(WebMapping.title.getMapping().name()));
                } else {
                    // for non-canonical documents we suppress indexing and write to crawler index only
                    updater
                        .put(CrawlerMapping.status_s.getMapping().name(), Status.noncanonical.name())
                        .put(CrawlerMapping.comment_t.getMapping().name(), docjson.optString("omitted, canonical: " + canonical_url));
                }

                // write crawler index
                try {
                    CrawlerDocument.update(super.config, super.config.gridIndex, urlid, updater);
                    // check with http://localhost:9200/crawler/_search?q=status_s:parsed
                } catch (final IOException e) {
                    // well that should not happen
                    Logger.warn("could not write crawler index", e);
                }

                // write graph document
                if (targetgraph_object != null) {
                    targetgraph_object.add(bulkjson);
                    final JSONObject graphjson = ParserService.extractGraph(docjson);
                    //graphjson.put("_id", id);
                    targetgraph_object.add(graphjson);
                }
            }

            boolean storeToMessage = true; // debug version for now: always true TODO: set to false later
            if (!storeToMessage || (archiveindex && Service.instance.config.gridStorage.isS3Connected())) {
                try {
                    final String targetasset = targetasset_object.toString();
                    super.config.gridStorage.store(targetasset_path, targetasset.getBytes(StandardCharsets.UTF_8));
                    Logger.info("Parser.processAction stored asset " + targetasset_path);
                } catch (final Throwable ee) {
                    Logger.warn("Parser.processAction asset " + targetasset_path + " could not be stored, carrying the asset within the next action", ee);
                    storeToMessage = true;
                }
            }
            if (!storeToMessage || (archivegraph && Service.instance.config.gridStorage.isS3Connected())) {
                try {
                    final String targetgraph = targetgraph_object.toString();
                    super.config.gridStorage.store(targetgraph_path, targetgraph.getBytes(StandardCharsets.UTF_8));
                    Logger.info("Parser.processAction stored graph " + targetgraph_path);
                } catch (final Throwable ee) {
                    Logger.warn("Parser.processAction asset " + targetgraph_path + " could not be stored, carrying the asset within the next action", ee);
                    storeToMessage = true;
                }
            }
            // emergency storage to message
            if (storeToMessage) {
                final JSONArray actions = action.getEmbeddedActions();
                actions.forEach(a -> {
                    new SusiAction((JSONObject) a).setJSONListAsset(targetasset_path, targetasset_object);
                    new SusiAction((JSONObject) a).setJSONListAsset(targetgraph_path, targetgraph_object);
                    Logger.info("Parser.processAction stored assets " + targetasset_path + ", " + targetgraph_path + " into message");
                });
            }
            Logger.info("Parser.processAction processed message from queue and stored asset " + targetasset_path);

            return ActionResult.SUCCESS;
        } catch (final Throwable e) {
            Logger.warn("", e);
            return ActionResult.FAIL_IRREVERSIBLE;
        }
    }

    @Override
    public Telemetry getTelemetry() {
        return null;
    }
}