/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.ai.core.api.messages;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.ai.core.api.support.Metadata;
import io.nop.ai.core.response.JsonResponseParser;
import io.nop.ai.core.response.XmlResponseParser;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_BLOCK_BEGIN;
import static io.nop.ai.core.AiCoreErrors.ARG_BLOCK_END;
import static io.nop.ai.core.AiCoreErrors.ARG_CONTENT;
import static io.nop.ai.core.AiCoreErrors.ARG_EXPECTED;
import static io.nop.ai.core.AiCoreErrors.ARG_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_INVALID_RESPONSE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_INVALID_END_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_NO_EXPECTED_PART;
import static io.nop.ai.core.commons.debug.DebugMessageHelper.collectDebugText;

@DataBean
public class AiChatResponse extends Metadata {
    static final Logger LOG = LoggerFactory.getLogger(AiChatResponse.class);

    /**
     * 此次消息所对应的prompt
     */
    private Prompt prompt;

    private Integer index;
    private MessageStatus status;

    private AiAssistantMessage message;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /**
     * 接下响应消息得到的结果对象
     */
    private Map<String, Object> outputs;

    /**
     * 用于保存上下文相关变量
     */
    private Map<String, Object> attributes;

    /**
     * 解析output失败会设置invalid为true
     */
    private boolean invalid;
    private ErrorBean invalidReason;

    public AiChatResponse() {
        this.message = new AiAssistantMessage();
    }

    public AiChatResponse(AiAssistantMessage message) {
        this.message = message;
    }

    public String getContent() {
        return message.getContent();
    }

    public void setContent(String content) {
        this.message.setContent(content);
    }

    public boolean isEmpty() {
        return StringHelper.isEmpty(getContent()) && outputs == null;
    }

    public String getBlockFromPrompt(String blockBegin, String blockEnd) {
        return getBlockFromPrompt(blockBegin, blockEnd, 0);
    }

    public String getBlockFromPrompt(String blockBegin, String blockEnd, int blockIndex) {
        if (prompt == null)
            return null;

        String message = prompt.getLastMessage().getContent();
        if (message == null)
            return null;

        int[] pos = indexOfMark(message, 0, blockBegin, blockIndex);
        if (pos == null)
            return null;

        int[] pos2 = indexOfMark(message, pos[1], blockEnd);
        if (pos2 == null)
            return null;

        return message.substring(pos[1], pos2[0]);
    }

    public String getThink() {
        return message.getThink();
    }

    public void setThink(String think) {
        this.message.setThink(think);
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public AiAssistantMessage getMessage() {
        return message;
    }

    public void setMessage(AiAssistantMessage message) {
        this.message = message;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public ErrorBean getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(ErrorBean invalidReason) {
        this.invalidReason = invalidReason;
    }

    @JsonIgnore
    public boolean isValid() {
        return !invalid;
    }

    public AiChatResponse validate() {
        if (!isValid()) {
            if (invalidReason != null)
                throw NopRebuildException.rebuild(invalidReason);
            throw new NopException(ERR_AI_INVALID_RESPONSE)
                    .param(ARG_CONTENT, StringHelper.limitLen(getContent(), 255));
        }
        return this;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    @JsonIgnore
    public Prompt getPrompt() {
        return prompt;
    }

    public void setPrompt(Prompt prompt) {
        this.prompt = prompt;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    public Object getOutput(String name) {
        if (outputs == null)
            return null;
        return outputs.get(name);
    }

    public void setOutput(String name, Object value) {
        if (outputs == null)
            outputs = new HashMap<>();
        outputs.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(String name) {
        if (attributes == null)
            return null;
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (attributes == null)
            attributes = new HashMap<>();
        attributes.put(name, value);
    }

    public String toDebugText() {
        StringBuilder sb = new StringBuilder();
        collectDebugText(sb, this);
        return sb.toString();
    }

    public boolean checkNotEmpty() {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return false;
        }
        return true;
    }

    /**
     * 检查结果的最后一行为指定内容，如果不是则抛出异常
     *
     * @param expected 期待返回的文本行
     */
    public boolean checkAndRemoveEndLine(String expected) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            LOG.debug("nop.err.ai.content-is-empty");

            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return false;
        }
        int pos = content.lastIndexOf('\n');
        if (pos == content.length() - 1) {
            pos = content.lastIndexOf('\n', pos - 1);
        }
        if (pos < 0) {
            LOG.debug("nop.err.ai.missing-expected-line:{}", expected);

            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected).param(ARG_LINE, "");
            setInvalid(true);
            return false;
        }

        String endLine = content.substring(pos + 1).trim();
        if (!endLine.equals(expected.trim())) {
            LOG.debug("nop.err.ai.end-line-not-match:expected={},endLine={}", expected, endLine);

            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected)
                    .param(ARG_LINE, endLine);
            setInvalid(true);
            return false;
        }

        setContent(content.substring(0, pos));
        return true;
    }

    public String requireBlock(String blockBegin, String blockEnd) {
        return requireBlock(blockBegin, blockEnd, false);
    }

    public String requireBlock(String blockBegin, String blockEnd, boolean optionalBegin) {
        return getBlock(blockBegin, blockEnd, optionalBegin, false);
    }

    public String parseContentBlock(String blockBegin, String blockEnd, boolean optionalBegin, boolean optional) {
        String block = getBlock(blockBegin, blockEnd, optionalBegin, optional);
        if (block != null) {
            setContent(block);
        }
        return block;
    }

    public String getBlock(String blockBegin, String blockEnd, boolean optionalBegin, boolean optional) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            if (optional)
                return null;

            LOG.debug("nop.err.ai.content-is-empty-when-get-block");

            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode())
                    .param(ARG_BLOCK_BEGIN, blockBegin)
                    .param(ARG_BLOCK_END, blockEnd);
            setInvalid(true);
            return null;
        }

        int[] markPos = indexOfMark(content, 0, blockBegin);
        if (markPos == null) {
            if (optional)
                return null;
            if (!optionalBegin) {
                LOG.debug("nop.err.ai.missing-block-begin:{}", blockBegin);

                invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                        .param(ARG_EXPECTED, blockBegin);
                setInvalid(true);
                return null;
            }
        }

        int pos = markPos == null ? 0 : markPos[1];

        int[] markPos2 = indexOfMark(content, pos, blockEnd);
        if (markPos2 == null) {
            if (optional)
                return null;
            LOG.debug("nop.err.ai.missing-block-end:{}", blockEnd);

            invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                    .param(ARG_EXPECTED, blockEnd);
            setInvalid(true);
            return null;
        }

        int pos2 = markPos2[0];
        return content.substring(pos, pos2);
    }

    public static int[] indexOfMark(String content, int start, String mark, int blockIndex) {
        int startPos = start;
        for (int i = 0; i < blockIndex; i++) {
            int[] pos = indexOfMark(content, startPos, mark);
            if (pos == null)
                return null;
            startPos = pos[1];
        }
        return indexOfMark(content, startPos, mark);
    }

    // 忽略无关紧要的空格
    public static int[] indexOfMark(String content, int start, String mark) {
        int pos = content.indexOf(mark, start);
        if (pos >= 0)
            return new int[]{pos, pos + mark.length()};
        String trimmedMark = mark.trim();
        pos = content.indexOf(trimmedMark, start);
        if (pos >= 0) {
            int pos0 = pos, pos1 = pos + trimmedMark.length();
            if (mark.startsWith("\n")) {
                int pos2 = content.lastIndexOf('\n', pos);
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos2 + 1, pos)))
                    return null;
                pos0 = pos2 + 1;
            }

            if (mark.endsWith("\n")) {
                int pos2 = content.indexOf('\n', pos1);
                if (pos2 < 0)
                    pos2 = content.length();
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos1, pos2)))
                    return null;
                pos1 = pos2;
            }

            return new int[]{pos0, pos1};
        }
        return null;
    }

    public boolean contentContains(String str) {
        String content = getContent();
        if (content == null)
            return false;
        return content.contains(str);
    }

    public XNode parseXmlContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;

        return new XmlResponseParser().parseResponse(content);
    }

    public Map<String, Object> parseJsonContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return new JsonResponseParser().parseResponse(content);
    }

    @Override
    public String toString() {
        return "AiChatResponse{" +
                "index=" + index +
                ", status=" + status +
                ", completionTokens=" + getCompletionTokens() +
                ", content='" + getContent() +
                '}';
    }
}
