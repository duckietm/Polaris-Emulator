package polaris.messages.rcon;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Comparator;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

final class RconPayloadValidator {
    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    private RconPayloadValidator() {}

    static String validate(Object payload) {
        if (payload == null) {
            return "invalid payload";
        }

        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(payload);
        return violations.stream()
                .min(Comparator.comparing(
                        violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .orElse(null);
    }
}
