/**
 *  GeoLocation
 *  Copyright 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 08.10.2009 on http://yacy.net
 *
 *  This file is part of YaCy Content Integration
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

package net.yacy.cora.geo;

import java.util.Comparator;

import net.yacy.cora.document.encoding.UTF8;


public class GeoLocation extends IntegerGeoPoint implements Comparable<GeoLocation>, Comparator<GeoLocation> {

    private byte[] name;
    private int population;

    public GeoLocation(final double lat, final double lon) {
        super(lat, lon);
        this.name = null;
        this.population = 0;
    }

    public GeoLocation(final double lat, final double lon, final String name) {
        super(lat, lon);
        this.name = UTF8.getBytes(name);
    }

    public void setName(final String name) {
        this.name = UTF8.getBytes(name);
    }

    public String getName() {
        return UTF8.String(this.name);
    }

    public void setPopulation(final int population) {
        this.population = population;
    }

    public int getPopulation() {
        return this.population;
    }

    @Override
    public boolean equals(final Object loc) {
        if (!(loc instanceof GeoLocation)) return false;
        if (this.name == null || ((GeoLocation) loc).name == null) return super.equals(loc);
        return super.equals(loc) && this.getName().toLowerCase().equals(((GeoLocation) loc).getName().toLowerCase());
    }

    /**
     * comparator that is needed to use the object inside TreeMap/TreeSet
     * a Location is smaller than another if it has a _greater_ population
     * this order is used to get sorted lists of locations where the first elements
     * have the greatest population
     */
    @Override
    public int compareTo(final GeoLocation o) {
        if (this.equals(o)) return 0;
        final long s = (ph(this.getPopulation()) << 30) + this.hashCode();
        final long t = (ph(o.getPopulation()) << 30) + o.hashCode();
        if (s > t) return -1;
        if (s < t) return  1;
        return 0;
    }

    private static long ph(int population) {
        if (population > 10000) population -= 10000;
        return population;
    }

    @Override
    public int compare(final GeoLocation o1, final GeoLocation o2) {
        return o1.compareTo(o2);
    }

    public static int degreeToKm(final double degree) {
        return (int) (degree * 111.32d);
    }

}
