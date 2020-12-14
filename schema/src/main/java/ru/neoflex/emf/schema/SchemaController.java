package ru.neoflex.emf.schema;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;

@RestController()
@RequestMapping("/schema")
public class SchemaController {
    final DBServerSvc dbServerSvc;

    public SchemaController(DBServerSvc dbServerSvc) {
        this.dbServerSvc = dbServerSvc;
    }

    @PostConstruct
    public void init() throws Exception {
        dbServerSvc.getDbServer().registerEPackage(SchemaPackage.eINSTANCE);
        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            Resource views = dbServerSvc.getDbServer().findBy(rs, SchemaPackage.eINSTANCE.getDBView());
            if (views.getContents().size() > 0) {
                return null;
            }

            DBTable group = SchemaFactory.eINSTANCE.createDBTable();
            group.setName("GROUP");
            Column group_id = SchemaFactory.eINSTANCE.createColumn();
            group_id.setName("ID");
            group_id.setDbType("INTEGER");
            group.getColumns().add(group_id);
            Column group_name = SchemaFactory.eINSTANCE.createColumn();
            group_name.setName("NAME");
            group_name.setDbType("STRING");
            group.getColumns().add(group_name);
            PKey group_pk = SchemaFactory.eINSTANCE.createPKey();
            group_pk.setName("group_pk");
            group_pk.getColumns().add(group_id);
            group.setPKey(group_pk);
            Resource group_res = rs.createResource(tx.getDbServer().createURI());
            group_res.getContents().add(group);
            group_res.save(null);

            DBTable user = SchemaFactory.eINSTANCE.createDBTable();
            user.setName("USER");
            Column user_id = SchemaFactory.eINSTANCE.createColumn();
            user_id.setName("ID");
            user_id.setDbType("INTEGER");
            user.getColumns().add(user_id);
            Column user_name = SchemaFactory.eINSTANCE.createColumn();
            user_name.setName("NAME");
            user_name.setDbType("STRING");
            user.getColumns().add(user_name);
            Column user_group_id = SchemaFactory.eINSTANCE.createColumn();
            user_group_id.setName("GROUP_ID");
            user_group_id.setDbType("INTEGER");
            user.getColumns().add(user_group_id);
            PKey user_pk = SchemaFactory.eINSTANCE.createPKey();
            user_pk.setName("user_pk");
            user_pk.getColumns().add(user_id);
            user.setPKey(user_pk);
            FKey user_group_fk = SchemaFactory.eINSTANCE.createFKey();
            user_group_fk.setName("user_group_fk");
            user_group_fk.getColumns().add(user_group_id);
            user_group_fk.setEntity(group);
            user.getFKeys().add(user_group_fk);
            Resource user_res = rs.createResource(tx.getDbServer().createURI());
            user_res.getContents().add(user);
            user_res.save(null);

            DBView user_group = SchemaFactory.eINSTANCE.createDBView();
            user_group.setName("USER_GROUP");
            user_group.getColumns().add(user_id);
            user_group.getColumns().add(user_name);
            user_group.getColumns().add(group_id);
            user_group.getColumns().add(group_name);
            Resource user_group_res = rs.createResource(tx.getDbServer().createURI());
            user_group_res.getContents().add(user_group);
            user_group_res.save(null);
            return null;
        });
    }
}
