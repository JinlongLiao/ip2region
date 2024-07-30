package org.lionsoul.ip2region.merge;

import java.util.Objects;

import static org.lionsoul.ip2region.merge.IpConveter.ipToLong;

public class RegionItem implements Comparable<RegionItem> {
    private String id = "";
    private long start;
    private long end;
    private String country;
    private String code = "0";
    private String province;
    private String city;
    private String isp = "0";

    public RegionItem() {
    }

    public RegionItem(String line) {
        String[] split = line.split("\\|");
        this.start = ipToLong(split[0]);
        this.end = ipToLong(split[1]);
        this.country = (split[2]);
        this.code = (split[3]);
        this.province = (split[4]);
        this.city = (split[5]);
        this.isp = (split[6]);
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getIsp() {
        return isp;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }

    public boolean between(String ip) {
        long l = ipToLong(ip);
        return between(l);
    }

    public boolean between(long ip) {
        return ip >= start && ip <= end;
    }

    public String getId() {
        return id;
    }

    @Override
    public int compareTo(RegionItem o) {
        return Long.compare(o.getStart(), getStart());
    }

    public boolean equal(RegionItem regionItem) {
        if (Objects.isNull(regionItem)) {
            return false;
        }
        return Objects.equals(getCountry(), regionItem.getCountry())
                && Objects.equals(getProvince(), regionItem.getProvince())
                && Objects.equals(getCity(), regionItem.getCity())
                && Objects.equals(getIsp(), regionItem.getIsp())
                ;
    }

    public void changeId() {
        this.id = String.valueOf(System.currentTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionItem)) return false;
        RegionItem that = (RegionItem) o;
        return getStart() == that.getStart()
                && getEnd() == that.getEnd()
                && Objects.equals(getCountry(), that.getCountry())
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getCode(), that.getCode())
                && Objects.equals(getProvince(), that.getProvince())
                && Objects.equals(getCity(), that.getCity()) && Objects.equals(getIsp(), that.getIsp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStart(), getId(), getEnd(), getCountry(), getCode(), getProvince(), getCity(), getIsp());
    }
}
