/**
 *  Ranking
 *  Copyright 2013 by Michael Peter Christen
 *  First released 12.03.2013 at http://yacy.net
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

package net.yacy.cora.federate.solr;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.yacy.grid.io.index.MappingDeclaration;

/**
 * The Ranking class is the solr ranking definition file for boosts and query functions.
 */
public class Ranking {
    
    // for minTokenLen = 2 the quantRate value should not be below 0.24; for minTokenLen = 3 the quantRate value must be not below 0.5!
    private static float quantRate = 0.5f; // to be filled with search.ranking.solr.doubledetection.quantrate
    private static int   minTokenLen = 3;   // to be filled with search.ranking.solr.doubledetection.minlength
    
    private Map<MappingDeclaration, Float> fieldBoosts;
    private String name, filterQuery, boostQuery, boostFunction, queryFields;
    
    public Ranking() {
        super();
        this.name = "";
        this.fieldBoosts = new LinkedHashMap<MappingDeclaration, Float>();
        this.filterQuery = "";
        this.boostQuery = "";
        this.boostFunction = "";
        this.queryFields = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public void putFieldBoost(MappingDeclaration schema, float boost) {
        this.fieldBoosts.put(schema,  boost);
    }

    public Float getFieldBoost(MappingDeclaration schema) {
        return this.fieldBoosts.get(schema);
    }
    
    public Set<Map.Entry<MappingDeclaration,Float>> getBoostMap() {
        return this.fieldBoosts.entrySet();
    }


    /**
     * set a filter query which will be added as fq-attribute to the query
     * @param filterQuery
     */
    public void setFilterQuery(String filterQuery) {
        this.filterQuery = filterQuery;
    }
    
    /**
     * get a string that can be added as a filter query at the fq-attribute
     * @return
     */
    public String getFilterQuery() {
        return this.filterQuery;
    }

    /**
     * set a boost query which will be added as bq-attribute to the query
     * @param boostQuery
     */
    public void setBoostQuery(String boostQuery) {
        this.boostQuery = boostQuery;
    }
    
    /**
     * get a string that can be added as a 'boost query' at the bq-attribute
     * @return
     */
    public String getBoostQuery() {
        return this.boostQuery;
    }

    public void setBoostFunction(String boostFunction) {
        this.boostFunction = boostFunction;
    }
    
    /**
     * produce a boost function
     * @return
     */
    public String getBoostFunction() {
        if (this.boostFunction.contains("last_modified")) return ""; // since solr 5.0 this does not work any more
        return this.boostFunction;
    }
    
    /*
     * duplicate check static methods
     */

    public static void setQuantRate(float newquantRate) {
        quantRate = newquantRate;
    }

    public static void setMinTokenLen(int newminTokenLen) {
        minTokenLen = newminTokenLen;
    }

    public static float getQuantRate() {
        return quantRate;
    }

    public static int getMinTokenLen() {
        return minTokenLen;
    }
    
}
