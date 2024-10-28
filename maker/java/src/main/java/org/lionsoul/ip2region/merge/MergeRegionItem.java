package org.lionsoul.ip2region.merge;

import java.util.Objects;

import static org.lionsoul.ip2region.merge.IpConveter.ipToLong;

public class MergeRegionItem implements Comparable<MergeRegionItem> {
    private long ip;
    private String country;
    private String province;
    private String city;

    public MergeRegionItem() {
    }

    public MergeRegionItem(String line) {
        String[] split = line.split(",");
        if (split.length != 7) {
            System.err.println("split = " + split);
        }
        this.ip = ipToLong(split[0]);
        this.country = (split[4]);
        this.province = (split[5]);
        try {
            this.city = (split[6]);

        }catch (RuntimeException e) {
            throw e;
        }
    }

    public long getIp() {
        return ip;
    }

    public void setIp(long ip) {
        this.ip = ip;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @Override
    public int compareTo(MergeRegionItem o) {
        return Long.compare(o.getIp(), getIp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MergeRegionItem)) return false;
        MergeRegionItem that = (MergeRegionItem) o;
        return getIp() == that.getIp() && Objects.equals(getCountry(), that.getCountry()) && Objects.equals(getProvince(), that.getProvince()) && Objects.equals(getCity(), that.getCity());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIp(), getCountry(), getProvince(), getCity());
    }
}
