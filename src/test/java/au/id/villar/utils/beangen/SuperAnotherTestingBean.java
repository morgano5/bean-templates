package au.id.villar.utils.beangen;

public class SuperAnotherTestingBean {

	private int amount;

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public static class SuperAnotherTestingBeanBuilder {
		private int amount;

		public SuperAnotherTestingBeanBuilder amount(int amount) {
			this.amount = amount;
			return this;
		}

		public SuperAnotherTestingBean build() {
			SuperAnotherTestingBean bean = new SuperAnotherTestingBean();
			bean.setAmount(this.amount);
			return bean;
		}
	}

	public static SuperAnotherTestingBeanBuilder builder() {
		return new SuperAnotherTestingBeanBuilder();
	}

	public SuperAnotherTestingBeanBuilder toBuilder() {
		return builder().amount(this.amount);
	}
}
