package io.nop.ai.core.commons.splitter;

import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextSplitter implements IAiTextSplitter {

    @Override
    public List<SplitChunk> split(String text, int maxContentSize) {
        if (text.length() <= maxContentSize)
            return List.of(new SplitChunk(null, text));

        List<SplitChunk> ret = new ArrayList<>();
        text = StringHelper.replace(text, "\r\n", "\n");
        List<String> parts = StringHelper.split(text, '\n');
        // 删除最后一个空行
        if (parts.get(parts.size() - 1).isEmpty()) {
            parts.remove(parts.size() - 1);
        }

        int index = 0;
        do {
            index = collectOneChunk(parts, index, maxContentSize, ret);
        } while (index > 0);
        return ret;
    }

    protected int collectOneChunk(List<String> parts, int index, int maxContentSize,
                                  List<SplitChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = index, n = parts.size(); i < n; i++) {
            String line = parts.get(i);
            if (sb.length() + line.length() <= maxContentSize) {
                sb.append(line).append('\n');
            } else if (sb.length() > 0) {
                chunks.add(new SplitChunk("text", sb.toString()));
                sb.setLength(0);
                return i;
            } else {
                // 一行太长，直接分隔。
                String part = getPart(line, maxContentSize);
                parts.set(i, line.substring(part.length() + 1));
                chunks.add(new SplitChunk("text", part));
                return i;
            }
        }
        if (sb.length() > 0) {
            chunks.add(new SplitChunk("text", sb.toString()));
        }
        return -1;
    }

    protected String getPart(String line, int maxContentSize) {
        String part = line.substring(0, maxContentSize);
        int index = part.lastIndexOf('。');
        if (index <= 0) {
            index = part.lastIndexOf('.');
            if (index <= 0)
                index = part.lastIndexOf(',');
            if (index <= 0)
                return part;
        }
        return part.substring(0, index + 1);
    }
}
