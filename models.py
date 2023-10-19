from sqlalchemy import Column, Integer, String, Text, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from database import Base


class User(Base):
    __tablename__ = "userinfo"

    id = Column(String, primary_key=True)
    puuid = Column(String, primary_key=True)
    name = Column(String)

