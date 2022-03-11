/**
 *  JSONLDValidatorService
 *  Copyright 13.6.2018 by Michael Peter Christen, @0rb1t3r
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

package net.yacy.grid.parser.api;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.json.JSONArray;
import org.json.JSONObject;

import net.yacy.document.Document;
import net.yacy.document.parser.htmlParser;
import net.yacy.grid.http.APIHandler;
import net.yacy.grid.http.ClientConnection;
import net.yacy.grid.http.ObjectAPIHandler;
import net.yacy.grid.http.Query;
import net.yacy.grid.http.ServiceResponse;
import net.yacy.grid.mcp.Service;

/**
 * call examples:
 * http://127.0.0.1:8500/yacy/grid/parser/jsonldvalidator.json?etherpad=05cc1575f55de2dc82f20f9010d71358
 * http://127.0.0.1:8500/yacy/grid/parser/jsonldvalidator.json?url=http://ebay.de
 * http://127.0.0.1:8500/yacy/grid/parser/jsonldvalidator.json?url=https://release-8-0-x-dev-224m2by-lj6ob4e22x2mc.eu.platform.sh/test
 */
public class JSONLDValidatorService extends ObjectAPIHandler implements APIHandler {

    private static final long serialVersionUID = 85784631749879L;
    public static final String NAME = "jsonldvalidator";

    @Override
    public String getAPIPath() {
        return "/yacy/grid/parser/" + NAME + ".json";
    }

    @Override
    public ServiceResponse serviceImpl(final Query call, final HttpServletResponse response) {

        final String url = call.get("url", "");

        final String etherpad = call.get("etherpad", "");
        final String etherpad_urlstub = Service.instance.config.properties.getOrDefault("parser.etherpad.urlstub", "");
        final String etherpad_apikey = Service.instance.config.properties.getOrDefault("parser.etherpad.apikey", "");

        final JSONObject json = new JSONObject(true);
        json.put(ObjectAPIHandler.SUCCESS_KEY, false);
        json.put(ObjectAPIHandler.COMMENT_KEY, "you must submit either a 'etherpad' or 'url' object");

        if (url.length() > 0) {
            try {
                final byte[] b = ClientConnection.load(url);
                final Document[] docs = htmlParser.parse(url, b);

                final String s = htmlParser.RDFa2JSONLDExpandString(url, b);
                final JSONArray jaExpand = new JSONArray(s);
                final JSONArray jaFlatten = new JSONArray(htmlParser.JSONLDExpand2Mode(url, s, JSONLDMode.FLATTEN));
                final JSONObject jaCompact = new JSONObject(htmlParser.JSONLDExpand2Mode(url, s, JSONLDMode.COMPACT));
                final String compactString = jaCompact.toString(2); // store the compact json-ld into a string because compact2tree is destructive
                final JSONObject jaTree = htmlParser.compact2tree(jaCompact);

                json.put("ld", docs[0].ld());
                json.put("ldnew-expand", jaExpand);
                json.put("ldnew-flat", jaFlatten);
                json.put("ldnew-compact", new JSONObject(compactString));
                json.put("ldnew-tree", jaTree);
                json.put(ObjectAPIHandler.COMMENT_KEY, "parsing of url content successfull");
            } catch (final Throwable e) {
                json.put(ObjectAPIHandler.COMMENT_KEY, "parsing of url content failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (etherpad.length() > 0 && etherpad_urlstub.length() > 0 && etherpad_apikey.length() > 0) {
            try {
                final String content = ClientConnection.loadFromEtherpad(etherpad_urlstub, etherpad_apikey, etherpad);
                final Document[] docs = htmlParser.parse(content);
                json.put("ld", docs[0].ld());
                json.put(ObjectAPIHandler.COMMENT_KEY, "parsing of etherpad successfull");
            } catch (final IOException e) {
                e.printStackTrace();
                json.put(ObjectAPIHandler.COMMENT_KEY, "parsing of etherpad failed: " + e.getMessage());
            }
        }

        return new ServiceResponse(json);
    }

}
