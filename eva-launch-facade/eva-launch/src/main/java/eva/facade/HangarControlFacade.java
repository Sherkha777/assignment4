package eva.facade;

import eva.catapult.CatapultPneumatics;
import eva.catapult.ShaftCondition;
import eva.pilot.EntryPlugLauncher;
import eva.power.PowerGridRouter;

import java.util.ArrayList;
import java.util.List;

public class HangarControlFacade {

    private final PowerGridRouter grid;
    private final CatapultPneumatics pneumatics;
    private final LaunchOrderRepository orders;
    private final ChainCompensator compensator;
    private final OrderViewAssembler assembler;

    public HangarControlFacade(EntryPlugLauncher launcher, PowerGridRouter grid, CatapultPneumatics pneumatics,
                               LaunchOrderRepository orders) {
        this.grid = grid;
        this.pneumatics = pneumatics;
        this.orders = orders;
        this.compensator = new ChainCompensator(launcher, grid, orders);
        this.assembler = new OrderViewAssembler(launcher, grid, pneumatics);
    }

    public List<OrderView> listOrders() {
        List<OrderView> views = new ArrayList<>();
        for (LaunchOrder order : orders.findAll()) {
            views.add(assembler.assemble(order));
        }
        return views;
    }

    public OrderView inspect(long orderId) {
        return assembler.assemble(load(orderId));
    }

    public OrderView manualCompensate(long orderId) {
        LaunchOrder order = load(orderId);
        if (order.status().isTerminal()) {
            throw new OrderNotCompensableException(orderId, order.status());
        }
        compensator.compensate(order, "manual compensation by operator");
        return assembler.assemble(order);
    }

    public void setBayPower(String chassisId, double megawatts) {
        grid.setBayAvailableMegawatts(chassisId, megawatts);
    }

    public double bayPower(String chassisId) {
        return grid.bayAvailableMegawatts(chassisId);
    }

    public void setShaftCondition(String shaftId, boolean doorOpen, boolean debris) {
        pneumatics.setShaftCondition(shaftId, doorOpen, debris);
    }

    public String shaftReport(String shaftId) {
        ShaftCondition condition = pneumatics.shaftCondition(shaftId);
        return shaftId + ": door " + (condition.doorOpen() ? "open" : "sealed")
                + ", debris " + (condition.debris() ? "present" : "none");
    }

    private LaunchOrder load(long orderId) {
        return orders.find(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
