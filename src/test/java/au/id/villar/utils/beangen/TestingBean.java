package au.id.villar.utils.beangen;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;

@Table(name = "PERSONS")
@BeanTemplate(typeName = "PersonEntity")
public class TestingBean<R, S> extends SuperTestingBean {

    public static final String CONSTANT = "zzz";

    @Id
    @Column(name = "PERSON_ID", nullable = false)
    protected int id;

    @Column(name = "PERSON_NAME", nullable = false)
    protected String givenName;

    @Column(name = "PERSON_SURNAME", nullable = false)
    protected String lastName;

    @Column(name = "PERSON_AMOUNT")
    protected BigDecimal amount;

    protected int[] scores;

    protected List<R> otherField;

    protected final String readOnlyField = "READ_ONLY";


    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int[] getScores() {
        return scores;
    }

    public void setScores(int[] scores) {
        this.scores = scores;
    }

    public void setScores(String[] scores) {
        // TODO;
    }

    public <T> List<String> notAField(String one, List<T> two) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestingBean)) return false;
        TestingBean<?, ?> that = (TestingBean<?, ?>) o;
        return id == that.id && Objects.equals(givenName, that.givenName) && Objects.equals(lastName, that.lastName) && Objects.equals(amount, that.amount) && Arrays.equals(scores, that.scores) && Objects.equals(otherField, that.otherField) && Objects.equals(readOnlyField, that.readOnlyField);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, givenName, lastName, amount, otherField, readOnlyField);
        result = 31 * result + Arrays.hashCode(scores);
        return result;
    }

    @Builder
    public TestingBean(int id, String givenName, String lastName, BigDecimal amount, int[] scores, List<R> otherField) {
        this.id = id;
        this.givenName = givenName;
        this.lastName = lastName;
        this.amount = amount;
        this.scores = scores;
        this.otherField = otherField;
    }
}
