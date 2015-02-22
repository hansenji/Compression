package com.vikingsen.compression;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

public class HuffmanCoder {

    private static final String COMPRESS_FLAG = "-c";
    private static final String DECOMPRESS_FLAG = "-d";
    private static final int BUFFER_SIZE = 512;
    private static final int SCALE = 7;
    private static final TerminatorNode TERMINATOR_NODE = new TerminatorNode();

    private Map<Byte, Integer> probabilityMap = new HashMap<>();
    private Map<Byte, List<Boolean>> encodingMap = new HashMap<>();
    private PriorityQueue<LeafNode> leafQueue = new PriorityQueue<>();
    private PriorityQueue<InnerNode> innerQueue = new PriorityQueue<>();

    public static void main(String[] args) {
        if (args.length != 3 || !validateFlag(args[0])) {
            System.err.println("Usage: HuffmanCoder (-c|-d) inFile outFile");
            System.exit(1);
        }

        try {
            HuffmanCoder coder = new HuffmanCoder();
            if (COMPRESS_FLAG.equalsIgnoreCase(args[0])) {
                coder.compress(args[1], args[2]);
            } else {
                coder.decompress(args[1], args[2]);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e);
            throw new IllegalStateException(e);
//            System.exit(2);
        }
        System.out.println("Finished");

    }

    private static boolean validateFlag(String flag) {
        return COMPRESS_FLAG.equalsIgnoreCase(flag) || DECOMPRESS_FLAG.equalsIgnoreCase(flag);
    }

    private void compress(String in, String out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];

        int readBytes;
        BigInteger totalBytes = BigInteger.ZERO;
        try(BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(in))) {
            while ((readBytes = inputStream.read(buffer, 0, BUFFER_SIZE)) > 0) {
                for (int i = 0; i < readBytes; i++) {
                    byte b = buffer[i];
                    if (probabilityMap.containsKey(b)) {
                        probabilityMap.put(b, probabilityMap.get(b) + 1);
                    } else {
                        probabilityMap.put(b, 1);
                    }
                }
                totalBytes = totalBytes.add(BigInteger.valueOf(readBytes));
            }
        }

        for (byte b : probabilityMap.keySet()) {
            int p = getPercentage(probabilityMap.get(b), totalBytes);
            probabilityMap.put(b, p);
        }

        leafQueue.offer(TERMINATOR_NODE);
        for (byte b : probabilityMap.keySet()) {
            leafQueue.offer(new LeafNode(b, probabilityMap.get(b)));
        }

        // Create tree
        while (!leafQueue.isEmpty() || innerQueue.size() > 1) {
            HuffmanNode left = getNextNode();
            HuffmanNode right = getNextNode();

            InnerNode node = new InnerNode(left, right);

            prependBit(left, false);
            prependBit(right, true);

            innerQueue.offer(node);
        }
        InnerNode root = innerQueue.poll();

        // Output Table
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(out))) {
            writeTable(probabilityMap, outputStream);

            BitOutputStream bitOutputStream = new BitOutputStream(outputStream);
            // Encode bytes;
            try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(in))) {
                while ((readBytes = inputStream.read(buffer, 0, BUFFER_SIZE)) > 0) {
                    for (int i = 0; i < readBytes; i++) {
                        for (boolean b : encodingMap.get(buffer[i])) {
                            bitOutputStream.writeBit(b);
                        }
                    }
                }
            }

            for (Boolean b : encodingMap.get(null)) { // Terminator node.
                bitOutputStream.writeBit(b);
            }
            bitOutputStream.flush();
        }
    }

    private void decompress(String in, String out) throws IOException {
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(in))) {
            probabilityMap = readTable(inputStream);

            leafQueue.offer(TERMINATOR_NODE);
            for (Byte b : probabilityMap.keySet()) {
                leafQueue.offer(new LeafNode(b, probabilityMap.get(b)));
            }

            // Create tree
            while (!leafQueue.isEmpty() || innerQueue.size() > 1) {
                HuffmanNode left = getNextNode();
                HuffmanNode right = getNextNode();

                InnerNode node = new InnerNode(left, right);

                prependBit(left, false);
                prependBit(right, true);

                innerQueue.offer(node);
            }

            InnerNode root = innerQueue.poll();

            // Decode Bits and Output File
            BitInputStream bitInputStream = new BitInputStream(inputStream);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bufferPosition = 0;
            try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(out))) {
                Boolean bit;
                InnerNode current = root;
                boolean terminatorFound = false;
                while ((bit = bitInputStream.readBit()) != null) {
                    HuffmanNode next = bit ? current.right : current.left;
                    if (next instanceof TerminatorNode) {
                        terminatorFound = true;
                        break;
                    } else if (next instanceof LeafNode) {
                        buffer[bufferPosition] = ((LeafNode) next).b;
                        bufferPosition++;
                        if (bufferPosition == BUFFER_SIZE) {
                            outputStream.write(buffer, 0, bufferPosition);
                            bufferPosition = 0;
                        }
                        current = root;
                    } else {
                        current = (InnerNode)next;
                    }
                }
                if (!terminatorFound) {
                    throw new IOException("Unable to decompress file");
                }
                outputStream.write(buffer, 0, bufferPosition);
                outputStream.flush();
            }
        }
    }

    private HuffmanNode getNextNode() {
        if (!leafQueue.isEmpty()) {
            if (!innerQueue.isEmpty()) {
                if (leafQueue.peek().compareTo(innerQueue.peek()) <= 0) {
                    return leafQueue.poll();
                }
                return innerQueue.poll();
            }
            return leafQueue.poll();
        }
        return innerQueue.poll();
    }

    private void prependBit(HuffmanNode node, boolean bit) {
        if (node instanceof LeafNode) {
            Byte b = ((LeafNode) node).b;
            List<Boolean> bits;
            if (encodingMap.containsKey(b)) {
                bits = encodingMap.get(b);
            } else {
                bits = new LinkedList<>();
                encodingMap.put(b, bits);
            }
            bits.add(0, bit);
        } else if (node instanceof InnerNode) {
            prependBit(((InnerNode) node).left, bit);
            prependBit(((InnerNode) node).right, bit);
        }
    }

    private int getPercentage(int count, BigInteger total) {
        BigDecimal dividend = BigDecimal.valueOf(count);
        BigDecimal quotient = dividend.divide(new BigDecimal(total), SCALE, BigDecimal.ROUND_HALF_UP);
        BigDecimal value = quotient.movePointRight(SCALE);
        return value.intValueExact();
    }

    private void writeTable(Map<Byte, Integer> probabilityMap, OutputStream outputStream) throws IOException {
        int count = probabilityMap.size(); // Remove Terminator
        byte[] buffer = new byte[(count * 4) + 1];
        buffer[0] = (byte) count;
        int i = 1;
        for (Byte b : probabilityMap.keySet()) {
            if (b == null) {
                continue;
            }
            int p = probabilityMap.get(b);
            buffer[i] = b;
            buffer[i+1] = (byte) ((p & 0xFF0000) >> 16);
            buffer[i+2] = (byte) ((p & 0xFF00) >> 8);
            buffer[i+3] = (byte) (p & 0xFF);
            i += 4;
        }
        outputStream.write(buffer, 0, buffer.length);
        outputStream.flush();
    }

    private Map<Byte, Integer> readTable(InputStream inputStream) throws IOException {
        Map<Byte, Integer> map = new HashMap<>();
        byte[] countBuffer = new byte[1];
        int read = inputStream.read(countBuffer, 0, 1);
        if (read != 1) {
            throw new IOException("Unable to read table count");
        }
        int count = (countBuffer[0] & 0xFF) * 4;
        byte[] buffer = new byte[count];
        read = inputStream.read(buffer, 0, count);
        if (read != count) {
            throw new IOException("Unable to read table count");
        }

        for (int i = 0; i < count; i += 4) {
            byte b = buffer[i];
            int p = (buffer[i+1] & 0xFF) << 16;
            p |= (buffer[i+2]  & 0xFF) << 8;
            p |= (buffer[i+3] & 0xFF);
            map.put(b, p);
        }

        return map;
    }

    private void printTree(InnerNode root, String file) throws IOException {
        String innerNodeDef = "N%1$d [label=\"\"];\n";
        String leafNodeDef = "N%1$d [label=\"0x%2$s\"];\n";
        String nodeLink = "N%d -> N%d [label=\"%d\"];\n";
        try (PrintWriter out = new PrintWriter(file)) {
            out.write("digraph G {\n");
            int counter = 0;
            Queue<Node> queue = new LinkedList<>();
            queue.offer(new Node(counter++, root));

            while(!queue.isEmpty()) {
                Node node = queue.poll();
                if (node.node instanceof LeafNode) {
                    Byte b = ((LeafNode) node.node).b;
                    String s = b != null ? Integer.toHexString(b & 0xFF) : "Null";
                    out.write(String.format(leafNodeDef, node.key, s));
                } else if (node.node instanceof InnerNode) {
                    out.write(String.format(innerNodeDef, node.key));
                    Node left = new Node(counter++, ((InnerNode) node.node).left);
                    Node right = new Node(counter++, ((InnerNode) node.node).right);
                    out.write(String.format(nodeLink, node.key, left.key, 0));
                    out.write(String.format(nodeLink, node.key, right.key, 1));
                    queue.offer(left);
                    queue.offer(right);
                }
            }
            out.write(String.format("t [label=\"Total Nodes: %d\"];", counter));
            out.write("}");
            out.flush();
        }
    }

    private void printTable(String file) throws IOException {
        try (PrintWriter out = new PrintWriter(file)) {
            for (Byte b : probabilityMap.keySet()) {
                String key = b == null ? "null" : Integer.toHexString(b & 0xFF);
                out.write(String.format("0x%s, %s\n", key, probabilityMap.get(b)));
            }
            out.flush();
        }
    }

    private void printCode(List<Boolean> bits) {
        for (Boolean bit : bits) {
            System.out.print(bit ? 1 : 0);
        }
        System.out.println();
    }

    private static class HuffmanNode implements Comparable<HuffmanNode> {
        protected int count;

        public HuffmanNode(int count) {
            this.count = count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @Override
        public int compareTo(HuffmanNode o) {
            return Integer.compare(this.count, o.count);
        }
    }

    private static class LeafNode extends HuffmanNode implements Comparable<HuffmanNode> {
        public Byte b;

        public LeafNode(Byte b, int count) {
            super(count);
            this.b = b;
        }

        @Override
        public int compareTo(HuffmanNode o) {
            int i = super.compareTo(o);
            if (i == 0) {
                if (o instanceof LeafNode) {
                    return Byte.compare(this.b, ((LeafNode)o).b);
                } else {
                    return -1;
                }

            }
            return i;
        }

        @Override
        public String toString() {
            return b + ": " + count;
        }
    }

    private static class InnerNode extends HuffmanNode implements Comparable<HuffmanNode> {

        public HuffmanNode left;
        public HuffmanNode right;

        public InnerNode(HuffmanNode left, HuffmanNode right) {
            super(left.count + right.count);
            this.left = left;
            this.right = right;
        }

        @Override
        public int compareTo(HuffmanNode o) {
            int i = super.compareTo(o);
            if (i == 0) {
                if (o instanceof InnerNode) {
                    return left.compareTo(((InnerNode)o).left);
                } else {
                    return 1;
                }
            }
            return i;
        }
    }

    private static class TerminatorNode extends LeafNode implements Comparable<HuffmanNode> {

        public TerminatorNode() {
            super(null, 0);
        }

        @Override
        public int compareTo(HuffmanNode o) {
            return 1; // Always the lowest Should get code all zeroes;
        }
    }

    private static class Node {
        public final int key;
        public final HuffmanNode node;

        public Node(int key, HuffmanNode node) {
            this.key = key;
            this.node = node;
        }


    }
}
