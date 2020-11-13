package ru.neoflex.emf.hibernatedb;

import javax.persistence.*;

@Entity
public class DBObject {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Column()
    private Integer version;
    @Column(name = "class_uri", length = 512)
    private String classUri;
    @Column(name = "q_name", length = 512)
    private String qName;
    @Column(length = 10485760)
    private byte[] image;
}
