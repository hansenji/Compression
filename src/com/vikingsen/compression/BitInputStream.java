package com.vikingsen.compression;

import java.io.IOException;
import java.io.InputStream;

public class BitInputStream {

    private static final int BUFFER_SIZE = 512;
    private static final int BYTE_SIZE = 8;

    private byte[] buffer = new byte[BUFFER_SIZE];
    private int bufferPosition = 0;
    private int bufferCount = -1;
    private int bytePosition = 0;

    private final InputStream inputStream;

    public BitInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public Boolean readBit() throws IOException {
        Boolean bit = null;
        if (bufferCount == -1) {
            fillBuffer();
        }
        if (bufferCount != 0) {
            byte b = buffer[bufferPosition];
            bit = ((b >> BYTE_SIZE - bytePosition - 1) & 0x1) == 1;
            bytePosition++;
            if (bytePosition == BYTE_SIZE) {
                bytePosition = 0;
                bufferPosition++;
                if (bufferPosition == bufferCount) {
                    bufferCount = -1;
                    bufferPosition = 0;
                }
            }
        }
        return bit;
    }

    private void fillBuffer() throws IOException {
        bufferCount = inputStream.read(buffer, 0, BUFFER_SIZE);
        bufferPosition = 0;
        bytePosition = 0;
    }
}
