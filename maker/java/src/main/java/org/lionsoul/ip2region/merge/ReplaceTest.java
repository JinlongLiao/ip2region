package org.lionsoul.ip2region.merge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ReplaceTest {

    public static void main(String[] args) throws IOException {

        File allFile = new File("D:\\work\\work\\ip2region\\data\\ip.merge.txt");
        List<String> strings = Files.readAllLines(allFile.toPath());
        List<String> strings2 = new ArrayList<>();
        for (String line : strings) {
            strings2.add(line.replaceAll("省\\|", "\\|").replaceAll("市\\|", "\\|"));
        }
        allFile.createNewFile();
        Files.write(allFile.toPath(), strings2);
    }

}
