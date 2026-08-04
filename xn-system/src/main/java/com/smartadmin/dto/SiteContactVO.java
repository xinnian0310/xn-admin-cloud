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
        private String value;

        /** 可点击链接（邮箱 mailto: / 网址 https:） */
        private String link;
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
        company.setValue("心念科技");
        vo.getContacts().add(company);

        ContactItem email = new ContactItem();
        email.setIcon("Message");
        email.setLabel("邮箱");
        email.setValue("support@xinnian.com");
        email.setLink("mailto:support@xinnian.com");
        vo.getContacts().add(email);

        ContactItem website = new ContactItem();
        website.setIcon("Link");
        website.setLabel("官网");
        website.setValue("https://xinnian.example.com");
        website.setLink("https://xinnian.example.com");
        vo.getContacts().add(website);

        ContactItem group = new ContactItem();
        group.setIcon("ChatDotRound");
        group.setLabel("交流群");
        group.setValue("123456789");
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
