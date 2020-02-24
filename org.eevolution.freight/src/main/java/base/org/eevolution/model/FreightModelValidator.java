/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2016 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 *****************************************************************************/

package org.eevolution.model;

import org.compiere.model.I_M_Shipper;
import org.compiere.model.MClient;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MWarehouse;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.Env;
import org.eevolution.engine.freight.FreightEngine;
import org.eevolution.engine.freight.FreightEngineFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Model Validator to Calculate Freight
 * Created by eEvolution author Victor Perez <victor.perez@e-evolution.com> 21/08/16.
 */
public class FreightModelValidator implements ModelValidator {

    FreightEngine freightEngine;

    @Override
    public void initialize(ModelValidationEngine engine, MClient client) {
        Integer clientId;
        if (client != null)
            clientId = client.getAD_Client_ID();
        else
            clientId = getAD_Client_ID();

        freightEngine  = FreightEngineFactory.getFreightEngine(clientId);
        engine.addModelChange(MOrderLine.Table_Name, this);
        engine.addModelChange(MWMInOutBoundLine.Table_Name, this);
        engine.addModelChange(MDDOrderLine.Table_Name, this);

        engine.addDocValidate(MOrder.Table_Name, this);
        engine.addDocValidate(MDDOrder.Table_Name, this);
        engine.addDocValidate(MWMInOutBound.Table_Name, this);
    }

    @Override
    public int getAD_Client_ID() {
        return Env.getAD_Client_ID(Env.getCtx());
    }

    @Override
    public String login(int orgId, int roleId, int userId) {
        return null;
    }

    /**
     * @param po     persistent object
     * @param timing see TIMING_ constants
     * @return
     */
    @Override
    public String docValidate(PO po, int timing) {

        if (ModelValidator.TIMING_AFTER_COMPLETE == timing) {
            if (po instanceof MOrder) {
                MOrder order = (MOrder) po;
                AtomicReference<BigDecimal> totalFreight = new AtomicReference<>(BigDecimal.ZERO);
                Arrays.stream(order.getLines()).forEach(orderLine -> {
                    totalFreight.getAndUpdate(freightAmt -> freightAmt.add(orderLine.getFreightAmt()));
                });
                order.setFreightAmt(totalFreight.get());
                order.saveEx();
            }

            if (po instanceof MDDOrder) {
                MDDOrder order = (MDDOrder) po;
                AtomicReference<BigDecimal> totalFreight = new AtomicReference<>(BigDecimal.ZERO);
                order.getLines().forEach(orderLine -> {
                    totalFreight.getAndUpdate(freightAmt -> freightAmt.add(orderLine.getFreightAmt()));
                });
                order.setFreightAmt(totalFreight.get());
                order.saveEx();
            }

            if (po instanceof MWMInOutBound) {
                MWMInOutBound order = (MWMInOutBound) po;
                AtomicReference<BigDecimal> totalFreight = new AtomicReference<>(BigDecimal.ZERO);
                order.getLines(false, null).forEach(orderLine -> {
                    if (MWMInOutBound.DELIVERYVIARULE_Shipper.equals(orderLine.getParent().getDeliveryViaRule()))
                        orderLine.setFreightAmt(getFreight(orderLine).multiply(orderLine.getMovementQty()));
                    totalFreight.getAndUpdate(freightAmt -> freightAmt.add(orderLine.getFreightAmt()));
                });
                order.setFreightAmt(totalFreight.get());
                order.saveEx();
            }
        }
        return null;
    }

    /**
     * @param po   persistent object
     * @param type TYPE_
     * @return
     * @throws Exception
     */
    @Override
    public String modelChange(PO po, int type) throws Exception {
        //Calculate Freight for Sales Order Line
        if (ModelValidator.TYPE_BEFORE_CHANGE == type || ModelValidator.TYPE_NEW == type ) {
            if (po instanceof MOrderLine) {
                MOrderLine orderLine = (MOrderLine) po;
                if (MOrder.DELIVERYVIARULE_Shipper.equals(orderLine.getParent().getDeliveryViaRule())) {
                    BigDecimal freightRate = getFreight(orderLine, null, null);
                    orderLine.setFreightAmt(freightRate.multiply(orderLine.getQtyOrdered()));
                    if (!MOrder.FREIGHTCOSTRULE_FreightIncluded.equals(orderLine.getParent().getFreightCostRule()) && freightRate.signum() != 0) {
                        BigDecimal price = orderLine.getPriceActual().add(freightRate);
                        orderLine.setPriceEntered(price);
                        orderLine.setPriceActual(price);
                        orderLine.setLineNetAmt();
                    }
                }
            }
            //Calculate Freight for Distribution Order Line
            if (po instanceof MDDOrderLine) {
                MDDOrderLine orderLine = (MDDOrderLine) po;
                if (MDDOrder.DELIVERYVIARULE_Shipper.equals(orderLine.getParent().getDeliveryViaRule()))
                    orderLine.setFreightAmt(getFreight(orderLine, null , null).multiply(orderLine.getQtyOrdered()));
            }
            //Calculate Freight  for Outbound Order Line
            if (po instanceof MWMInOutBoundLine) {
                MWMInOutBoundLine orderLine = (MWMInOutBoundLine) po;
                if (MWMInOutBound.DELIVERYVIARULE_Shipper.equals(orderLine.getParent().getDeliveryViaRule()))
                    orderLine.setFreightAmt(getFreight(orderLine).multiply(orderLine.getMovementQty()));
            }
        }
        return null;
    }

    /**
     * get Freight based on Sales Order Line and Outbound Order
     *
     * @param orderLine Sales Order Line
     * @param outboundOrder Outbound Order
     * @param outboundOrderLine Outbound Order Line
     * @return BigDecimal Freight Rate
     */
    private BigDecimal getFreight(MOrderLine orderLine, MWMInOutBound outboundOrder, MWMInOutBoundLine outboundOrderLine) {
        MOrder order = orderLine.getParent();
        MWarehouse warehouse = (MWarehouse) order.getM_Warehouse();
        Optional<I_M_Shipper> maybeShipper = Optional.ofNullable(Optional.ofNullable(outboundOrder).map(o -> {
            // Check if Outbound Order exist then use the Shipper of Outbound Order if not use Sales Order Shipper
           return MWMInOutBound.FREIGHTCOSTRULE_Line.equals(o.getFreightCostRule()) ? outboundOrderLine.getM_Shipper() : o.getM_Shipper();
            // Use the Shipper of Sales Order
        }).orElseGet(() -> {
           return  MOrder.FREIGHTCOSTRULE_Line.equals(order.getFreightCostRule()) ? orderLine.getM_Shipper() : order.getM_Shipper();
        }));
        if (maybeShipper.isPresent()) {
            int freightCategoryId = outboundOrder == null ? MOrder.FREIGHTCOSTRULE_Line.equals(order.getFreightCostRule()) ? orderLine.getM_FreightCategory_ID() : order.getM_FreightCategory_ID()
                    : outboundOrder.getM_FreightCategory_ID();
            if (isCalculatedFreight(order.getFreightCostRule()) && freightCategoryId > 0) {
                freightEngine = FreightEngineFactory.getFreightEngine(orderLine.getAD_Client_ID());
                return freightEngine.getFreightRuleFactory(maybeShipper.get(), order.getFreightCostRule())
                        .calculate(order.getCtx(),
                                orderLine.getM_Product_ID(),
                                maybeShipper.get().getM_Shipper_ID(),
                                warehouse.getC_Location_ID(),
                                order.getC_BPartner_Location().getC_Location_ID(),
                                freightCategoryId,
                                order.getC_Currency_ID(),
                                order.getDateOrdered(), order.get_TrxName());
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * get Freight based on Distribution Order and Outbound Order
     *
     * @param orderLine Sales Order Line
     * @param outboundOrder Outbound Order
     * @param outboundOrderLine Outbound Order Line
     * @return BigDecimal Freight Rate
     */
    private BigDecimal getFreight(MDDOrderLine orderLine, MWMInOutBound outboundOrder, MWMInOutBoundLine outboundOrderLine) {
        MDDOrder order = orderLine.getParent();
        Optional<I_M_Shipper> maybeShipper = Optional.ofNullable(Optional.ofNullable(outboundOrder).map(o -> {
            // Check if Outbound Order exist then use the Shipper of Outbound Order if not use Distribution Order Shipper
            return MWMInOutBound.FREIGHTCOSTRULE_Line.equals(o.getFreightCostRule()) ? outboundOrderLine.getM_Shipper() : o.getM_Shipper();
        }).orElseGet(() -> {
            // Use the Shipper of Distribution Order
            return  MDDOrder.FREIGHTCOSTRULE_Line.equals(order.getFreightCostRule()) ? orderLine.getM_Shipper() : order.getM_Shipper();
        }));
        if (maybeShipper.isPresent()) {
            int freightCategoryId = outboundOrder == null ? MDDOrder.FREIGHTCOSTRULE_Line.equals(order.getFreightCostRule()) ? orderLine.getM_FreightCategory_ID() : order.getM_FreightCategory_ID()
                    : outboundOrder.getM_FreightCategory_ID();
            if (isCalculatedFreight(order.getFreightCostRule()) && freightCategoryId > 0) {
                return freightEngine.getFreightRuleFactory(maybeShipper.get(), order.getFreightCostRule())
                        .calculate(order.getCtx(),
                                orderLine.getM_Product_ID(),
                                maybeShipper.get().getM_Shipper_ID(),
                                orderLine.getM_Locator().getM_Warehouse().getC_Location_ID(),
                                order.getC_BPartner_Location().getC_Location_ID(),
                                freightCategoryId,
                                order.getC_Currency_ID(),
                                order.getDateOrdered(), order.get_TrxName());
            }
        }
        return BigDecimal.ZERO;
    }

    private boolean isCalculatedFreight(String freightCostRule)
    {
        return  MOrder.FREIGHTCOSTRULE_Line.equals(freightCostRule)
                || MOrder.FREIGHTCOSTRULE_Calculated.equals(freightCostRule)
                || MOrder.FREIGHTCOSTRULE_FreightIncluded.equals(freightCostRule);

    }

    /**
     * get Freight based on Outbound Order Line
     *
     * @param orderLine Outbound Order Line
     * @return BigDecimal Freight Rate
     */
    private BigDecimal getFreight(MWMInOutBoundLine orderLine) {
        if (orderLine.getC_OrderLine_ID() > 0)
            return getFreight((MOrderLine) orderLine.getC_OrderLine(), orderLine.getParent() , orderLine);
        if (orderLine.getDD_OrderLine_ID() > 0)
            return getFreight((MDDOrderLine) orderLine.getDD_OrderLine(), orderLine.getParent() , orderLine);
        return BigDecimal.ZERO;
    }
}
