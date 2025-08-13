/**
 * MIT License
 *
 * Copyright (c) [2024] Zuora, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.zuora.sample;

import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

import com.zuora.ZuoraClient;
import com.zuora.model.*;

import com.google.gson.Gson;
import spark.utils.StringUtils;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;


public class Server {

    private static Gson gson = new Gson();
    /**
     * Please configure your OAuth client id here.
     */
    private static final String CLIENT_ID = "a37aac17-8546-4beb-97d3-825580f2cd7c";
    /**
     * Please configure your OAuth client secret here.
     */
    private static final String CLIENT_SECRET = "D0da2Q5+PtNXxujyzcTpf4SChUpnYizmuHxUsmJi";
    private final static ZuoraClient zuoraClient = new ZuoraClient(CLIENT_ID, CLIENT_SECRET, ZuoraClient.ZuoraEnv.CSBX);


    public static void main(String[] args) {
        port(8888);
        staticFiles.externalLocation(Paths.get("public").toAbsolutePath().toString());
        zuoraClient.setOrgIds("817afbb4-2a9a-4d83-bf35-5eeb3b6a6b25");
        zuoraClient.initialize();
        zuoraClient.setDebugging(true);

        post("create-payment-session", (request, response) -> {
            response.type("application/json");
            Map map = gson.fromJson(request.body(), Map.class);
            String firstName = (String) map.get("firstName");
            String lastName = (String) map.get("lastName");
            String address = (String) map.get("address");
            String city = (String) map.get("city");
            String state = (String) map.get("state");
            String zip = (String) map.get("zip");
            String country = (String) map.get("country");
            String email = (String) map.get("email");
            String currency = (String) map.get("currency");
            String amount = (String) map.get("amount");
            String paymentMethodType = (String) map.get("paymentMethodType");
            String paymentGatewayId;

            if (("creditcard".equals(paymentMethodType))) {
                paymentGatewayId = "8a8aa26697aeaa8b0197b17b1cde6ed6";
            } else {
                paymentGatewayId = "8a8aa26697aeaa8b0197b17b1cde6ed6";
            }

            final CreateAccountContact contact = new CreateAccountContact()
                    .firstName(firstName)
                    .lastName(lastName)
                    .address1(address)
                    .city(city)
                    .state(state)
                    .zipCode(zip)
                    .workEmail(email)
                    .country(country);
                    
            final CreateAccountRequest createAccountRequest = new CreateAccountRequest()
                    .name(String.join(" ", firstName, lastName))
                    .billToContact(contact)
                    .billCycleDay(0)
                    .soldToSameAsBillTo(true) 
                    .autoPay(false)
                    .currency(currency);

            final CreateAccountResponse createAccountResponse = zuoraClient.accountsApi().createAccountApi(createAccountRequest).execute();

            // Variable to store invoice number for payment session
            String invoiceNumber = null;
            
            // Create order with new subscription 
            try {
                // Create a simple order request
                final CreateOrderRequest orderRequest = new CreateOrderRequest();
                orderRequest.setOrderDate(java.time.LocalDate.now());
                orderRequest.setExistingAccountNumber(createAccountResponse.getAccountNumber());
                
                // Set processing options with runBilling and collectPayment enabled
                final ProcessingOptionsWithDelayedCapturePayment processingOptions = 
                    new ProcessingOptionsWithDelayedCapturePayment()
                        .runBilling(true)
                        .collectPayment(false);
                        
                orderRequest.processingOptions(processingOptions);

                // Create subscription with order action structure
                final CreateOrderSubscription subscription = new CreateOrderSubscription();
                
                // Create order action for creating subscription
                final CreateOrderAction orderAction = new CreateOrderAction();
                orderAction.setType(OrderActionType.CREATESUBSCRIPTION);
                
                // Create subscription details
                final CreateOrderCreateSubscription createSubscription = new CreateOrderCreateSubscription();
                
                // Set up terms with initial term and term type
                final InitialTerm initialTerm = new InitialTerm()
                   .termType(TermType.EVERGREEN);
                
                final OrderActionCreateSubscriptionTerms terms = new OrderActionCreateSubscriptionTerms()
                    .initialTerm(initialTerm);
                
                createSubscription.terms(terms);
                
                final CreateSubscribeToProduct subscribeToProduct = new CreateSubscribeToProduct()
                    .productRatePlanId("f74bba6d3b89818067281e1766f200ee");  // Replace with product rate plan ID(s) selected in cart
                
                createSubscription.subscribeToProducts(List.of(subscribeToProduct));
                
                // Set the subscription details on the order action
                orderAction.setCreateSubscription(createSubscription);
                subscription.orderActions(List.of(orderAction));
                
                // Add subscription to order
                orderRequest.setSubscriptions(List.of(subscription));
                
                // Execute the order creation
                final CreateOrderResponse createOrderResponse = zuoraClient.ordersApi().createOrderApi(orderRequest).execute();
                System.out.println("Order created successfully with ID: " + createOrderResponse.getOrderNumber());
                System.out.println("Order status: " + createOrderResponse.getStatus());
                
                // Extract invoice numbers if available
                if (createOrderResponse.getInvoiceNumbers() != null && !createOrderResponse.getInvoiceNumbers().isEmpty()) {
                    invoiceNumber = createOrderResponse.getInvoiceNumbers().get(0); // Get the first invoice
                    System.out.println("Invoice created: " + invoiceNumber);
                    System.out.println("Total invoices created: " + createOrderResponse.getInvoiceNumbers().size());
                } else {
                    System.out.println("No invoices were created by the order");
                }
                
            } catch (Exception e) {
                System.err.println("Error creating order: " + e.getMessage());
                e.printStackTrace();
                return gson.toJson(Map.of("error", "Failed to create order"));
            }

            // Create payment session request
            final CreatePaymentSessionRequest createPaymentSessionRequest = new CreatePaymentSessionRequest()
                    .currency(currency)
                    .amount(new BigDecimal(amount))
                    .processPayment(true)
                    .storePaymentMethod(true)
                    .accountId(createAccountResponse.getAccountId());
            //        .putAdditionalProperty("billingAddressLine1", "1001 Main St");
                    
            // Add invoice if one was created from the order
            if (StringUtils.isNotBlank(invoiceNumber)) {
                createPaymentSessionRequest.setInvoices(List.of(
                    new CreatePaymentSessionInvoice().invoiceNumber(invoiceNumber)
                ));
            }

            if (StringUtils.isNotBlank(paymentGatewayId)) {
                createPaymentSessionRequest.setPaymentGateway(paymentGatewayId);
            }

            final CreatePaymentSessionResponse createPaymentSessionResponse = zuoraClient.paymentMethodsApi().createPaymentSessionApi(createPaymentSessionRequest).execute();

            // Log the full response to see available fields
            System.out.println("Payment session response: " + gson.toJson(createPaymentSessionResponse));
            
            return gson.toJson(createPaymentSessionResponse.getToken());
        });
    }
}