package io.nop.ai.core.api.classifier;

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.annotations.data.DataBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DataBean
public class EmbeddingExampleConfig {
    private Map<String, List<String>> examplesByLabel;
    private int maxResults = 1;
    private double minScore = 0;
    private double meanToMaxScoreRatio = 0.5;
    private EmbeddingOptions embeddingOptions;

    public EmbeddingExampleConfig() {
    }

    /**
     * @param examplesByLabel     A map containing examples of texts for each label.
     *                            The more examples, the better. Examples can be easily generated by the LLM.
     * @param maxResults          The maximum number of labels to return for each classification.
     * @param minScore            The minimum similarity score required for classification, in the range [0..1].
     *                            Labels scoring lower than this value will be discarded.
     * @param meanToMaxScoreRatio A ratio, in the range [0..1], between the mean and max scores used for calculating
     *                            the final score.
     *                            During classification, the embeddings of examples for each label are compared to
     *                            the embedding of the text being classified.
     *                            This results in two metrics: the mean and max scores.
     *                            The mean score is the average similarity score for all examples associated with a given label.
     *                            The max score is the highest similarity score, corresponding to the example most
     *                            similar to the text being classified.
     *                            A value of 0 means that only the mean score will be used for ranking labels.
     *                            A value of 0.5 means that both scores will contribute equally to the final score.
     *                            A value of 1 means that only the max score will be used for ranking labels.
     */
    public EmbeddingExampleConfig(Map<String, List<String>> examplesByLabel, int maxResults, double minScore, double meanToMaxScoreRatio) {
        this.examplesByLabel = examplesByLabel;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.meanToMaxScoreRatio = meanToMaxScoreRatio;
    }

    public Map<String, List<VectorData>> getExampleEmbeddings(IEmbeddingModel embeddingModel) {
        Map<String, List<VectorData>> exampleEmbeddingsByLabel = new HashMap<>();
        examplesByLabel.forEach((label, examples) ->
                exampleEmbeddingsByLabel.put(label, embeddingModel.embedAll(
                                examples.stream().map(AiDocument::fromText).collect(Collectors.toList()), embeddingOptions
                        )
                ));
        return exampleEmbeddingsByLabel;
    }

    public EmbeddingOptions getEmbeddingOptions() {
        return embeddingOptions;
    }

    public void setEmbeddingOptions(EmbeddingOptions embeddingOptions) {
        this.embeddingOptions = embeddingOptions;
    }

    public Map<String, List<String>> getExamplesByLabel() {
        return examplesByLabel;
    }

    public void setExamplesByLabel(Map<String, List<String>> examplesByLabel) {
        this.examplesByLabel = examplesByLabel;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public double getMeanToMaxScoreRatio() {
        return meanToMaxScoreRatio;
    }

    public void setMeanToMaxScoreRatio(double meanToMaxScoreRatio) {
        this.meanToMaxScoreRatio = meanToMaxScoreRatio;
    }
}
