/**
 *  CaseInsensitiveMap
 *  Copyright 2022 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 04.04.2022 at http://yacy.net
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

package net.yacy.cora.storage;

import java.util.Map;
import java.util.TreeMap;

/**
 * Convenience Class for a TreeMap with String.CASE_INSENSITIVE_ORDER
 * This works different than org.apache.commons.collections4.map.CaseInsensitiveMap which
 * converts all keys to lowercase.
 * This class preserves the original keys.
 */
public class CaseInsensitiveMap<Obj> extends TreeMap<String, Obj> implements Map<String, Obj>{

	private static final long serialVersionUID = 6064164828003870141L;

	public CaseInsensitiveMap() {
		super(String.CASE_INSENSITIVE_ORDER);
	}
}
