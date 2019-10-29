package com.topcoder.scraper.module.ecisolatedmodule.kojima.crawler;

import static com.topcoder.common.util.HtmlUtils.*;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.topcoder.common.model.ProductInfo;
import com.topcoder.common.model.PurchaseHistory;
import com.topcoder.common.traffic.TrafficWebClient;
import com.topcoder.common.util.DateUtils;
import com.topcoder.scraper.exception.SessionExpiredException;
import com.topcoder.scraper.module.ecunifiedmodule.crawler.GeneralPurchaseHistoryListCrawlerResult;
import com.topcoder.scraper.lib.navpage.NavigablePurchaseHistoryPage;
import com.topcoder.scraper.service.WebpageService;

public class OldKojimaPurchaseHistoryListCrawler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OldKojimaPurchaseHistoryListCrawler.class);
  private static final Pattern PAT_ORDER_NO = Pattern.compile("([\\d]{13})", Pattern.DOTALL);
  private final String siteName;
  private final WebpageService webpageService;
  
  public OldKojimaPurchaseHistoryListCrawler(String siteName, WebpageService webpageService) {
    this.siteName = siteName;
    this.webpageService = webpageService;
  }

  public GeneralPurchaseHistoryListCrawlerResult fetchPurchaseHistoryList(TrafficWebClient webClient, PurchaseHistory lastPurchaseHistory, boolean saveHtml) throws IOException {
    List<PurchaseHistory> list = new LinkedList<>();
    List<String> pathList = new LinkedList<>();
    LOGGER.info("goto Order History Page");

    HtmlPage page = webClient.getPage("https://www.kojima.net/ec/member/CMmOrderHistory.jsp");
    if (page.getBaseURI().contains("?autoLogin")) {
      throw new SessionExpiredException("Session has been expired.");      
    }
    
    webpageService.save("kojima-purchase-history", siteName, page.getWebResponse().getContentAsString());
    while (true) {
      if (page == null || !parsePurchaseHistory(webClient, list, page, lastPurchaseHistory, saveHtml, pathList)) {
        break;
      }
      page = gotoNextPage(page, webClient);
    }

    return new GeneralPurchaseHistoryListCrawlerResult(list, pathList);
  }
  
  private boolean parsePurchaseHistory(TrafficWebClient webClient, List<PurchaseHistory> list, HtmlPage page, PurchaseHistory last, boolean saveHtml, List<String> pathList) {

    LOGGER.debug("Parsing page url " + page.getUrl().toString());
    


    //member-orderhistorydetails
    List<DomNode> orders = page.querySelectorAll(".member-orderhistorydetails > tbody");
    
    for (DomNode orderNode : orders) {
      PurchaseHistory purchaseHistory = new PurchaseHistory();
      NavigablePurchaseHistoryPage historyPage = new NavigablePurchaseHistoryPage(page, webClient, purchaseHistory);/// One purchase History? A list? 

      DomNode itemNoNode = orderNode.querySelector(".itemnumber");
      String nodeText = itemNoNode.asText();
      String orderNumber = extract(nodeText, PAT_ORDER_NO);
      LOGGER.info("ORDER NO: " + orderNumber);

      Date orderDate = extractDate(nodeText);
      LOGGER.info("ORDER DATE: " + orderDate);
      
      DomNode itemTotalAmountNode = orderNode.querySelector(".totalamountmoney");
      Integer totalAmount = itemTotalAmountNode != null ? extractInt(itemTotalAmountNode.asText()) : null;
      LOGGER.info("TOTAL: " + totalAmount);
            
      PurchaseHistory history = new PurchaseHistory(null, orderNumber, orderDate, totalAmount != null ? totalAmount.toString() : null, null, null);
      if (!isNew(history, last)) {
        LOGGER.info("SKIPPING: " + orderNumber);
        continue;        
      }
      
      List<DomNode> orderLines = orderNode.querySelectorAll("tr");
      List<ProductInfo> productInfoList = orderLines.stream().map(this::parseProduct).filter(p -> p.getName() != null).collect(Collectors.toList());
      history.setProducts(productInfoList);
      
      list.add(history);
    }
    return false;
  }
  
  
  private boolean isNew(PurchaseHistory purchase, PurchaseHistory last) {
    Date orderDate = purchase.getOrderDate();
    Date lastOrderDate = last != null ? last.getOrderDate() : null;
    String orderNumber = purchase.getOrderNumber();
    String lastOrderNo = last != null ? (last.getOrderNumber() != null ? last.getOrderNumber() : "") : "";

    if ((orderDate != null && lastOrderDate != null && orderDate.compareTo(lastOrderDate) <= 0) || lastOrderNo.equals(orderNumber)) {
      return false;
    }
    return true;
  }
  
  private ProductInfo parseProduct(DomNode orderLineNode) {

    DomNode itemNameNode = orderLineNode.querySelector(".itemname");
    if (itemNameNode == null) {
      return new ProductInfo();
    }
    String name = normalizeProductName(itemNameNode.asText());
    
    DomNode itemPriceNode = orderLineNode.querySelector(".itemprice");
    Integer price = itemPriceNode != null ? extractInt(itemPriceNode.asText()) : null;

    DomNode itemQtyNode = orderLineNode.querySelector(".itemnum");
    Integer quantity = itemQtyNode != null ? extractInt(itemQtyNode.asText()) : null;
    
    LOGGER.info(String.format("parseProduct::{Name:%s, Price:%d, Quantity:%s}", name, price, quantity));
    return new ProductInfo((String)null, name, price != null ? price.toString() : null, quantity, (String)null);
  }
  
  private HtmlPage gotoNextPage(HtmlPage page, TrafficWebClient webClient) throws IOException {
    return null;
  }
  
  
  private static final Pattern PAT_DATE = Pattern.compile("(20[\\d]{2}/[\\d]{2}/[\\d]{2} [\\d]{2}:[\\d]{2}:[\\d]{2})", Pattern.DOTALL);
  private static final String FORMAT_DATE = "yyyy/MM/dd HH:mm:ss";
  
  private String normalizeProductName(String productName) {
    if (productName == null) {
      return productName;
    }
    return productName.trim().replaceAll("　", " ");
  }
  
  private Date extractDate(String text) {
    String dateStr = extract(text, PAT_DATE);
    try {
      return DateUtils.fromString(dateStr, FORMAT_DATE);
    } catch (ParseException e) {
      LOGGER.error(String.format("Failed to parse the input '%s'. Error: %s", dateStr, e.getMessage()));
      e.printStackTrace();
      return null;
    }
  }
}
