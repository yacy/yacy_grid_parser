// CachedFileReader.java
// ---------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 09.09.2009 on http://yacy.net
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


package net.yacy.kelondro.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import net.yacy.grid.tools.Logger;


public final class CachedFileReader extends AbstractReader implements Reader {

    private final RandomAccessFile RAFile;
    private byte[] cache;
    private final int cachelen;

    public CachedFileReader(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        this.RAFile = new RandomAccessFile(this.file, "r");
        if (this.RAFile.length() < Integer.MAX_VALUE) {
        	this.cache = new byte[(int) this.RAFile.length()];
        	this.RAFile.seek(0);
        	this.RAFile.readFully(this.cache);
        } else {
        	this.cache = null;
        }
        this.cachelen = 0;
    }

    @Override
    public final synchronized long available() throws IOException {
        return this.length() - this.RAFile.getFilePointer();
    }

    @Override
    public final synchronized long length() throws IOException {
        return this.RAFile.length();
    }

    @Override
    public final synchronized void readFully(final byte[] b, final int off, int len) throws IOException {
        long seek = this.RAFile.getFilePointer();
        if (this.cache != null  && this.cachelen - seek >= len) {
            // read from cache
            System.arraycopy(this.cache, (int) (seek), b, off, len);
            this.RAFile.seek(seek + len);
            return;
        }
        // cannot use the cache
        this.RAFile.readFully(b, off, len);
        return;
    }

    @Override
    public final synchronized void seek(final long pos) throws IOException {
        this.RAFile.seek(pos);
    }

    @Override
    public final synchronized void close() {
        if (this.RAFile != null) try {
            try{this.RAFile.getChannel().close();} catch (final IOException e) {}
            this.RAFile.close();
        } catch (final IOException e) {
            Logger.warn("", e);
        }
        this.cache = null;
    }

    @Override
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

}
