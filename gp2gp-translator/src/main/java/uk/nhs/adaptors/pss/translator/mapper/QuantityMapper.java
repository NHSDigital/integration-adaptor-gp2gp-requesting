package uk.nhs.adaptors.pss.translator.mapper;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Quantity;
import org.hl7.fhir.dstu3.model.Quantity.QuantityComparator;
import org.hl7.v3.IVLPQ;
import org.hl7.v3.PQ;
import org.hl7.v3.PQInc;
import org.hl7.v3.PQR;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class QuantityMapper {
    private static final String UNIT_SYSTEM = "http://unitsofmeasure.org";

    public Quantity mapValueQuantity(IVLPQ value) {
        Quantity quantity = new Quantity();

        if (value.getHigh() != null) {
            setQuantityWithHighComparator(quantity, value.getHigh());
        } else if (value.getLow() != null) {
            setQuantityWithLowComparator(quantity, value.getLow());
        }

        return quantity;
    }

    public Quantity mapValueQuantity(PQ value) {
        Quantity quantity = new Quantity();

        setQuantityValueAndUnit(quantity, value.getValue(), value.getUnit(), value.getTranslation());

        return quantity;
    }

    public Quantity mapReferenceRangeQuantity(IVLPQ value) {
        Quantity quantity = new Quantity();

        if (value.getHigh() != null) {
            setQuantityValueAndUnit(quantity, value.getHigh().getValue(),
                    value.getHigh().getUnit(), value.getHigh().getTranslation());
        } else if (value.getLow() != null) {
            setQuantityValueAndUnit(quantity, value.getLow().getValue(),
                    value.getLow().getUnit(), value.getLow().getTranslation());
        }

        return quantity;
    }

    private void setUnit(Quantity quantity, String unit, List<PQR> translation) {
        if (StringUtils.isNotBlank(unit)) {
            if (translation != null && !translation.isEmpty()) {
                quantity.setUnit(translation.get(0).getOriginalText());
            } else {
                quantity.setUnit(unit);
                quantity.setSystem(UNIT_SYSTEM);
                quantity.setCode(unit);
            }
        }
    }

    private void setQuantityWithHighComparator(Quantity quantity, PQInc high) {
        if (high.isInclusive()) {
            quantity.setComparator(QuantityComparator.LESS_OR_EQUAL);
        } else {
            quantity.setComparator(QuantityComparator.LESS_THAN);
        }

        setQuantityValueAndUnit(quantity, high.getValue(), high.getUnit(), high.getTranslation());
    }

    private void setQuantityWithLowComparator(Quantity quantity, PQInc low) {
        if (low.isInclusive()) {
            quantity.setComparator(QuantityComparator.GREATER_OR_EQUAL);
        } else {
            quantity.setComparator(QuantityComparator.GREATER_THAN);
        }

        setQuantityValueAndUnit(quantity, low.getValue(), low.getUnit(), low.getTranslation());
    }

    private void setQuantityValueAndUnit(Quantity quantity, String value, String unit, List<PQR> translation) {
        setUnit(quantity, unit, translation);
        var decimalPlaceIndex = value.indexOf(".");
        var decimalPlaceCount = 0;
        if (decimalPlaceIndex != -1) {
            decimalPlaceCount = value.substring(decimalPlaceIndex).length() - 1;
        }
        quantity.setValue(new BigDecimal((value)).setScale(decimalPlaceCount, RoundingMode.CEILING));
    }
}
