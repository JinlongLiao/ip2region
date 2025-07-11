package org.lionsoul.ip2region.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class MergeTest {

    public static void printHelp(String[] args) {
        System.out.println("ip2region xdb merge");
        System.out.println("options:");
        System.out.println(" source.txt merge.txt");
    }

    public static void main(String[] args) throws IOException {
        List<RegionItem> regionItems = new ArrayList<>(1 << 16);
        Collection<MergeRegionItem> mergeRegionItems = new ArrayList<>(1 << 16);
        if (args.length < 2) {
            printHelp(args);
            return;
        }
        File allFile = new File(args[0]);
        File merge = new File(args[1]);
        loadAll(allFile, regionItems);
        loadMerge(merge, mergeRegionItems);
        mergeRegionItems = mergeRegionItems.stream().distinct()
                .sorted((o1, o2) -> -o1.compareTo(o2)).collect(Collectors.toList());
        for (MergeRegionItem regionItem : mergeRegionItems) {
            toMerge(regionItem, regionItems);
        }
        Comparator<RegionItem> regionItemComparator = (o1, o2) -> -o1.compareTo(o2);
        regionItems = regionItems.stream().distinct().sorted(regionItemComparator).collect(Collectors.toList());
        mergerSame(regionItems);
        regionItems.sort(regionItemComparator);
        String collect = regionItems.stream().map(n -> {
            long start = n.getStart();
            long end = n.getEnd();
            return IpConveter.longToIp(start) + "|"
                    + IpConveter.longToIp(end) + "|"
                    + n.getCountry() + "|"
                    + n.getCode() + "|"
                    + n.getProvince() + "|"
                    + n.getCity() + "|"
                    + n.getIsp()
                    ;
        }).collect(Collectors.joining("\r\n"));
        allFile.createNewFile();
        BufferedWriter bufferedWriter = Files.newBufferedWriter(allFile.toPath());
        bufferedWriter.write(collect);
        bufferedWriter.close();

    }

    private static void mergerSame(List<RegionItem> regionItems) {
        RegionItem prevRegionItem = null;
        List<RegionItem> rmExtra = new ArrayList<>();
        for (int i = 0; i < regionItems.size(); i++) {
            RegionItem regionItem = regionItems.get(i);
            if (regionItem.equal(prevRegionItem)) {
                prevRegionItem.setEnd(regionItem.getEnd());
                String isp1 = regionItem.getIsp();
                String isp2 = prevRegionItem.getIsp();
                if (i - regionItems.indexOf(prevRegionItem) > 3) {
                    System.out.println("isp2 = " + isp2);
                }
                String isp = isp1.length() > isp2.length() ? isp1 : isp2;
                prevRegionItem.setIsp(isp);
                rmExtra.add(regionItem);
                regionItem = prevRegionItem;
            }
            prevRegionItem = regionItem;
        }
        regionItems.removeAll(rmExtra);
    }

    private static void toMerge(MergeRegionItem mergeRegionItem, List<RegionItem> regionItems) {
        RegionItem regionItem = null;
        for (RegionItem item : regionItems) {
            if (item.between(mergeRegionItem.getIp())) {
                regionItem = item;
                break;
            }
        }
        if (Objects.isNull(regionItem)) {
            RegionItem e = new RegionItem();
            e.setCity(mergeRegionItem.getCity());
            e.setProvince(mergeRegionItem.getProvince());
            e.setCountry(mergeRegionItem.getCountry());
            long[] longs = IpConveter.ipStartAndEnd(mergeRegionItem.getIp());
            e.setStart(longs[0]);
            e.setEnd(longs[1]);
            regionItems.add(e);
//            String string = IpConveter.longToIp(mergeRegionItem.getIp());
            return;
        }
        String country = regionItem.getCountry();
        long start1 = regionItem.getStart();
        if (Objects.equals(country, mergeRegionItem.getCountry())
                && (
                Objects.equals(regionItem.getProvince(), mergeRegionItem.getProvince()) ||
                        regionItem.getProvince().startsWith(mergeRegionItem.getProvince())
                        || mergeRegionItem.getProvince().startsWith(regionItem.getProvince()
                ))
                && (
                Objects.equals(regionItem.getCity(), mergeRegionItem.getCity()) ||
                        regionItem.getCity().startsWith(mergeRegionItem.getCity())
                        || mergeRegionItem.getCity().startsWith(regionItem.getCity()))
        ) {
            System.out.println("ip  = " + mergeRegionItem.getIp() + " regionItem = " + IpConveter.longToIp(start1));
            return;
        }
        long[] longs = IpConveter.ipStartAndEnd(mergeRegionItem.getIp());
        long start = longs[0];
        long end = longs[1];
        long end1 = regionItem.getEnd();
        if (end > end1) {
            end = end1;
        }
        if (start1 > start) {
            start = start1;
        }
        String code = regionItem.getCode();
        String isp = regionItem.getIsp();
        if (end1 == end && start1 == start) {
            regionItem.setCountry(mergeRegionItem.getCountry());
            regionItem.setProvince(mergeRegionItem.getProvince());
            regionItem.setCity(mergeRegionItem.getCity());
        } else if (start == start1) {
            regionItem.setEnd(end);
            //尾
            RegionItem e = new RegionItem();
            e.setCity(mergeRegionItem.getCity());
            e.setProvince(mergeRegionItem.getProvince());
            e.setCountry(mergeRegionItem.getCountry());
            e.setStart(end + 1);
            e.setEnd(end1);
            e.setCode(code);
            e.setIsp(isp);
            regionItems.add(e);
        } else if (end1 == end) {
            regionItem.setEnd(start - 1);
            //尾
            RegionItem e = new RegionItem();
            e.setCity(mergeRegionItem.getCity());
            e.setProvince(mergeRegionItem.getProvince());
            e.setCountry(mergeRegionItem.getCountry());
            e.setStart(start);
            e.setEnd(end);
            e.setCode(code);
            e.setIsp(isp);
            regionItems.add(e);

        } else if (end < end1) {
            //头
            regionItem.setEnd(start - 1);
            //中
            RegionItem e = new RegionItem();
            e.setCity(regionItem.getCity());
            e.setProvince(regionItem.getProvince());
            e.setCountry(regionItem.getCountry());
            e.setStart(start);
            e.setEnd(end);
            e.setCode(code);
            e.setIsp(isp);
            regionItems.add(e);
            //尾
            e = new RegionItem();
            e.setCity(mergeRegionItem.getCity());
            e.setProvince(mergeRegionItem.getProvince());
            e.setCountry(mergeRegionItem.getCountry());
            e.setStart(end + 1);
            e.setEnd(end1);
            e.setCode(code);
            e.setIsp(isp);
            regionItems.add(e);
        }
    }

    private static void loadMerge(File file, Collection<MergeRegionItem> mergeRegionItems) throws IOException {

        BufferedReader bufferedReader = Files.newBufferedReader(file.toPath());
        while (true) {
            String string = bufferedReader.readLine();
            if (Objects.isNull(string) || string.isEmpty()) break;
            System.out.println("string = " + string);
            mergeRegionItems.add(new MergeRegionItem(string));
        }
        bufferedReader.close();

    }

    private static void loadAll(File file, List<RegionItem> regionItems) throws IOException {

        BufferedReader bufferedReader = Files.newBufferedReader(file.toPath());
        while (true) {
            String string = bufferedReader.readLine();
            if (Objects.isNull(string) || string.isEmpty()) break;
            regionItems.add(new RegionItem(string));
        }
        bufferedReader.close();
    }

}
