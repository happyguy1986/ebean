package com.avaje.tests.model.prnt;

import javax.persistence.*;

@Entity
public class MPrinterState {

  @Id
  Long id;

  @Version
  Long version;

  long flags;

  @JoinColumn(name="printer_id")
  @ManyToOne
  MPrinter printer;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public long getFlags() {
    return flags;
  }

  public void setFlags(long flags) {
    this.flags = flags;
  }

  public MPrinter getPrinter() {
    return printer;
  }

  public void setPrinter(MPrinter printer) {
    this.printer = printer;
  }
}