package kbee.rag.thesaurus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DefaultWindowSplitter
        implements WindowSplitter {

    @Override
    public List<String> split(
            String text) {

        if (text == null
                || text.isBlank()) {

            return List.of();
        }

        List<String> windows =
                new ArrayList<>();

        for (String sentence :
                splitSentences(text)) {

            if (!isUsefulSentence(
                    sentence
            )) {
                continue;
            }

            windows.addAll(
                    buildWindows(
                            sentence
                    )
            );
        }

        return windows.stream()
                .distinct()
                .toList();
    }

    private List<String> splitSentences(
            String text) {

        return Arrays.stream(
                        text.split(
                                "(?<=[.!?])\\s+"
                        )
                )
                .map(String::trim)
                .filter(sentence ->
                        !sentence.isBlank()
                )
                .toList();
    }

    private boolean isUsefulSentence(
            String sentence) {

        if (sentence == null) {
            return false;
        }

        return sentence
                .trim()
                .length() >= 40;
    }

    private List<String> buildWindows(
            String sentence) {

        if (sentence == null
                || sentence.isBlank()) {

            return List.of();
        }

        String[] words =
                sentence
                        .trim()
                        .split("\\s+");

        List<String> windows =
                new ArrayList<>();

        addWindows(
                windows,
                words,
                10,
                5,
                3
        );

        addWindows(
                windows,
                words,
                20,
                10,
                6
        );

        return windows;
    }

    private void addWindows(
            List<String> windows,
            String[] words,
            int windowSize,
            int step,
            int minLength) {

        if (words.length <= windowSize) {

            if (words.length >= minLength) {

                windows.add(
                        String.join(
                                " ",
                                words
                        )
                );
            }

            return;
        }

        for (int start = 0;
                start < words.length;
                start += step) {

            int end =
                    Math.min(
                            start + windowSize,
                            words.length
                    );

            int length =
                    end - start;

            if (length < minLength) {
                break;
            }

            windows.add(
                    String.join(
                            " ",
                            Arrays.copyOfRange(
                                    words,
                                    start,
                                    end
                            )
                    )
            );

            if (end == words.length) {
                break;
            }
        }
    }
}