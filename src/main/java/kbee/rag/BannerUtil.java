package kbee.rag;


import java.util.HashMap;
import java.util.Map;

public class BannerUtil {

    private static final int HEIGHT = 6;

    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {

        // A
        FONT.put('A', new String[]{
                "   ___   ",
                "  / _ \\  ",
                " / /_\\ \\ ",
                " |  _  | ",
                " | | | | ",
                " \\_| |_/ "
        });

        // B
        FONT.put('B', new String[]{
                " _____  ",
                "| __  | ",
                "| __ -| ",
                "|_____| ",
                "|_____| ",
                "        "
        });

        // C
        FONT.put('C', new String[]{
                "  _____ ",
                " /  __ \\",
                " | /  \\/",
                " | |    ",
                " | \\__/\\",
                "  \\____/"
        });

        // D
        FONT.put('D', new String[]{
                " _____  ",
                "|  _  \\ ",
                "| | \\  |",
                "| |  | |",
                "| |_/  |",
                "|_____/ "
        });

        // E
        FONT.put('E', new String[]{
                " _____ ",
                "|  ___|",
                "| |__  ",
                "|  __| ",
                "| |___ ",
                "|_____|"
        });

        // K
        FONT.put('K', new String[]{
                " _   __ ",
                "| | / / ",
                "| |/ /  ",
                "|    \\  ",
                "| |\\  \\ ",
                "\\_| \\_/ "
        });

        // L
        FONT.put('L', new String[]{
                " _     ",
                "| |    ",
                "| |    ",
                "| |    ",
                "| |___ ",
                "|_____|"
        });

        // M
        FONT.put('M', new String[]{
                "___  ___",
                "|  \\/  |",
                "| .  . |",
                "| |\\/| |",
                "| |  | |",
                "\\_|  |_/"
        });

        // U
        FONT.put('U', new String[]{
                " _    _ ",
                "| |  | |",
                "| |  | |",
                "| |  | |",
                "| |__| |",
                " \\____/ "
        });

        // S
        FONT.put('S', new String[]{
                " _____ ",
                "/  ___|",
                "\\ `--. ",
                " `--. \\",
                "/\\__/ /",
                "\\____/ "
        });

        // V
        FONT.put('V', new String[]{
                " _   _ ",
                "| | | |",
                "| | | |",
                "| | | |",
                "\\ \\_/ /",
                " \\___/ "
        });

        // espacio
        FONT.put(' ', new String[]{
                "   ",
                "   ",
                "   ",
                "   ",
                "   ",
                "   "
        });
    }

    public static String[] generateBanner(String text) {

        text = text.toUpperCase();

        StringBuilder[] lines = new StringBuilder[HEIGHT];
        for (int i = 0; i < HEIGHT; i++)
            lines[i] = new StringBuilder();

        for (char c : text.toCharArray()) {

            String[] letter = FONT.getOrDefault(c, FONT.get(' '));

            for (int i = 0; i < HEIGHT; i++) {
                lines[i].append(letter[i]).append(" ");
            }
        }

        String[] result = new String[HEIGHT];
        for (int i = 0; i < HEIGHT; i++)
            result[i] = lines[i].toString();

        return result;
    }
}