package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 站点联系与捐赠配置（管理端首页 / 官网共用）。 */
@Getter
@Setter
public class SiteContactVO {

    private List<ContactItem> contacts = new ArrayList<>();
    private Donation donation = new Donation();

    @Getter
    @Setter
    public static class ContactItem {
        private String icon;
        private String label;

        /** text | link | email | qq */
        private String type;

        private String value;

        /** 可点击链接（邮箱 mailto: / 网址 https:） */
        private String link;

        /** QQ群可配置多个群号；其它分类为空 */
        private List<GroupItem> groups;
    }

    /** QQ 群号。注意：boolean 字段勿用 Lombok isXxx，否则 Jackson 可能读写不到 full。 */
    public static class GroupItem {
        private String value;
        private boolean full;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean getFull() {
            return full;
        }

        public void setFull(boolean full) {
            this.full = full;
        }
    }

    @Getter
    @Setter
    public static class Donation {
        private String tip;
        private List<Qrcode> qrcodes = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Qrcode {
        private String label;
        private String src;
    }

    /** 与前端历史静态默认值对齐 */
    public static SiteContactVO defaults() {
        SiteContactVO vo = new SiteContactVO();

        ContactItem company = new ContactItem();
        company.setIcon("User");
        company.setLabel("公司");
        company.setType("text");
        company.setValue("心念科技");
        vo.getContacts().add(company);

        ContactItem email = new ContactItem();
        email.setIcon("Message");
        email.setLabel("邮箱");
        email.setType("email");
        email.setValue("support@xinnian.com");
        email.setLink("mailto:support@xinnian.com");
        vo.getContacts().add(email);

        ContactItem website = new ContactItem();
        website.setIcon("Link");
        website.setLabel("官网");
        website.setType("link");
        website.setValue("https://xinnian.example.com");
        website.setLink("https://xinnian.example.com");
        vo.getContacts().add(website);

        ContactItem group = new ContactItem();
        group.setIcon("ChatDotRound");
        group.setLabel("交流群");
        group.setType("qq");
        group.setValue("123456789");
        GroupItem g1 = new GroupItem();
        g1.setValue("123456789");
        g1.setFull(false);
        group.setGroups(new ArrayList<>(List.of(g1)));
        vo.getContacts().add(group);

        Donation donation = new Donation();
        donation.setTip("如果这个项目对你有帮助，欢迎请作者喝杯咖啡");
        Qrcode wechat = new Qrcode();
        wechat.setLabel("微信支付");
        wechat.setSrc("");
        Qrcode alipay = new Qrcode();
        alipay.setLabel("支付宝");
        alipay.setSrc("");
        donation.getQrcodes().add(wechat);
        donation.getQrcodes().add(alipay);
        vo.setDonation(donation);
        return vo;
    }
}
