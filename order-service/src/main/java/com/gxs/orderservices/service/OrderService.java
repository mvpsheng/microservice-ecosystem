package com.gxs.orderservices.service;

import brave.Span;
import brave.Tracer;

import com.gxs.orderservices.dto.InventoryResponse;
import com.gxs.orderservices.dto.OrderLineItemsDto;
import com.gxs.orderservices.dto.OrderRequest;

import com.gxs.orderservices.model.Order;
import com.gxs.orderservices.model.OrderLineItems;
import com.gxs.orderservices.repository.OrderRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
//    private final Tracer tracer;
//    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());

        InventoryResponse[] inventoryResponsesArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponsesArray).allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }



//        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");
//
//        try (Tracer.SpanInScope isLookup = tracer.withSpanInScope(inventoryServiceLookup.start())) {
//
//            inventoryServiceLookup.tag("call", "inventory-service");
//            // Call Inventory Service, and place order if product is in
//            // stock
//            InventoryResponse[] inventoryResponsArray = webClientBuilder.build().get()
//                    .uri("http://inventory-service/api/inventory",
//                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
//                    .retrieve()
//                    .bodyToMono(InventoryResponse[].class)
//                    .block();
//
//            boolean allProductsInStock = Arrays.stream(inventoryResponsArray)
//                    .allMatch(InventoryResponse::isInStock);
//
//            if (allProductsInStock) {
//                orderRepository.save(order);
//                kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
//                return "Order Placed Successfully";
//            } else {
//                throw new IllegalArgumentException("Product is not in stock, please try again later");
//            }
//        } finally {
//            inventoryServiceLookup.flush();
//        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
