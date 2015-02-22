package com.vikingsen.compression;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class BitOutputStream {

    private static final int BUFFER_SIZE = 512;
    private static final int BYTE_SIZE = 8;

    private byte[] buffer = new byte[BUFFER_SIZE];
    private int bufferPosition = 0;
    private byte currentByte = 0;
    private int bytePosition = 0;

    private final OutputStream outputStream;

    public BitOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void writeBit(boolean b) throws IOException {
        int bit = b ? 1 : 0;
        currentByte |= bit << BYTE_SIZE - bytePosition - 1;
        bytePosition++;
        if (bytePosition == BYTE_SIZE) {
            appendByte();
            if (bufferPosition == BUFFER_SIZE) {
                flushBuffer();
            }
        }
    }

    public void flush() throws IOException {
        if (bytePosition > 0) {
            appendByte();
        }
        flushBuffer();
    }

    private void appendByte() {
        buffer[bufferPosition] = currentByte;
        bufferPosition++;
        resetByte();
    }

    private void flushBuffer() throws IOException {
        if (bufferPosition > 0) {
            outputStream.write(buffer, 0, bufferPosition);
            resetBuffer();
        }
    }

    private void resetByte() {
        currentByte = 0;
        bytePosition = 0;
    }

    private void resetBuffer() {
        bufferPosition = 0;
        Arrays.fill(buffer, (byte) 0);
    }
}
