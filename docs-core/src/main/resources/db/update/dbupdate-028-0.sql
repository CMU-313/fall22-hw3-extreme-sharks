create memory table T_REVIEW ( REV_ID_C varchar(36) not null, REV_IDROUTESTEP_C varchar(36) not null, REV_CATEGORY_C varchar(36) not null, REV_VALUE_DEC decimal(3, 2) not null, primary key (REV_ID_C) );

alter table T_REVIEW add constraint FK_REV_IDROUTESTEP_C foreign key (REV_IDROUTESTEP_C) references T_ROUTE_STEP (RTP_ID_C) on delete cascade on update cascade;

update T_CONFIG set CFG_VALUE_C = '28' where CFG_ID_C = 'DB_VERSION';
